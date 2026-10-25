package com.verdant.salon_ecomm.services;

import com.stripe.StripeClient;
import com.stripe.exception.*;
import com.stripe.model.*;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.verdant.salon_ecomm.config.StripeConfig;
import com.verdant.salon_ecomm.dtos.giftcards.events.GiftCardStripeWebhookEvent;
import com.verdant.salon_ecomm.dtos.giftcards.events.OrderPaymentVoidedEvent;
import com.verdant.salon_ecomm.dtos.payment.CreatePaymentInput;
import com.verdant.salon_ecomm.dtos.payment.OrderPaymentDisputedEvent;
import com.verdant.salon_ecomm.dtos.payment.PaymentIntentDto;
import com.verdant.salon_ecomm.dtos.payment.PaymentStatusChangedEvent;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.entities.stripe.WebhookEvent;
import com.verdant.salon_ecomm.exceptions.InvalidWebhookSignatureException;
import com.verdant.salon_ecomm.exceptions.PaymentException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.PaymentMapper;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import com.verdant.salon_ecomm.repositories.OrderRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.repositories.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    // H-3: FAILED is deliberately NOT terminal. payment_intent.payment_failed puts the intent back to
    // requires_payment_method and the customer can retry the same intent (createPaymentIntent hands
    // it back), so a later requires_action / processing / succeeded must still be applied.
    // M-14: PARTIALLY_REFUNDED is settled money too: only a further (partial or full) refund may follow.
    private static final Set<PaymentStatus> TERMINAL_STATUSES = EnumSet.of(
        PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED, PaymentStatus.CANCELLED
    );

    // Statuses from which a refund (charge.refunded, or a lost dispute) may still move the order on.
    private static final Set<PaymentStatus> REFUNDABLE_STATUSES = EnumSet.of(
        PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED
    );

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentMapper paymentMapper;
    private final StripeConfig stripeConfig;
    private final StripeClient stripeClient;

    private final ApplicationEventPublisher eventPublisher;

    // Self-injected proxy so @Retryable actually applies (a plain `this.` call
    // bypasses the Spring AOP proxy). Set via constructor/setter injection by
    // Spring since it's declared as a dependency of this same bean type.
    @Lazy
    private final PaymentService self;

    // ---------- Mutations ----------

    public PaymentIntentDto createPaymentIntent(CreatePaymentInput input, UUID currentUserId, boolean isAdmin) {
        Order order = self.loadAndAuthorizeOrder(input.orderId(), currentUserId, isAdmin);

        // M-17 flow: check payable -> (existing intent? hand it back) -> create the intent at Stripe ->
        // persistPaymentIntentId re-checks under the row lock -> if the order died in between, cancel the
        // new intent and refuse. The re-check closes the window between this check and the Stripe call.
        requirePayable(order);

        if (order.getStripePaymentIntentId() != null) {
            return paymentMapper.toDto(retrieveExistingIntent(order.getStripePaymentIntentId()));
        }

        User user = order.getUser();
        if (user == null) {
            throw new PaymentException(
                "Order " + order.getId() + " has no associated user; cannot create a Stripe customer"
            );
        }

        String stripeCustomerId = self.ensureStripeCustomer(user);

        PaymentIntent intent;
        try {
            intent = self.createStripePaymentIntent(order, stripeCustomerId);
        } catch (StripeException ex) {
            throw new PaymentException("Failed to create PaymentIntent for order " + order.getId(), ex);
        }

        Order updated = self.persistPaymentIntentId(order.getId(), order.getUser().getId(), intent.getId());
        // M-17: cancelled, expired or refunded while the intent was being created. The intent was not
        // saved on the order; cancel it at Stripe (outside any DB transaction) so it can never be paid.
        if (!isPayable(updated)) {
            cancelIntentQuietly(intent.getId());
            requirePayable(updated); // throws with the matching message
        }
        if (!intent.getId().equals(updated.getStripePaymentIntentId())) {
            // Lost the race — another request already persisted its intent id first.
            return paymentMapper.toDto(retrieveExistingIntent(updated.getStripePaymentIntentId()));
        }

        return paymentMapper.toDto(intent);
    }

    // Statuses Stripe allows us to cancel. "processing" is left out on purpose: the money may already
    // be moving, and that order should be left to finish rather than expired.
    private static final Set<String> CANCELABLE_INTENT_STATUSES = Set.of(
        "requires_payment_method", "requires_confirmation", "requires_action", "requires_capture"
    );

    // H-5: used by UnpaidOrderExpiryJob before it returns an abandoned order's stock. Cancelling at
    // Stripe first is what makes the expiry safe: after this the customer can't pay that intent.
    // Returns true if the intent is now canceled (or already was), false if it can't be (e.g. it
    // succeeded or is processing), in which case the order must NOT be expired.
    public boolean cancelPaymentIntentForExpiry(String paymentIntentId) {
        try {
            PaymentIntent intent = stripeClient.paymentIntents().retrieve(paymentIntentId);
            if ("canceled".equals(intent.getStatus())) return true;
            if (!CANCELABLE_INTENT_STATUSES.contains(intent.getStatus())) return false;
            stripeClient.paymentIntents().cancel(paymentIntentId);
            return true;
        } catch (StripeException ex) {
            // e.g. payment_intent_unexpected_state: it changed between retrieve and cancel. Keep the order.
            log.warn("Could not cancel PaymentIntent {} for expiry: {}", paymentIntentId, ex.getMessage());
            return false;
        }
    }

    public void deleteStripeCustomer(String stripeCustomerId) {
        if (stripeCustomerId == null) {
            return;
        }
        try {
            stripeClient.customers().delete(stripeCustomerId);
        } catch (StripeException ex) {
            // Already gone (deleted earlier, or by a previous attempt that failed after the call): nothing left to do.
            if ("resource_missing".equals(ex.getCode())) {
                return;
            }
            throw new PaymentException("Failed to delete Stripe customer " + stripeCustomerId, ex);
        }
    }

    // ---------- Webhook handling ----------

    @Transactional
    public void handleStripeWebhook(String rawPayload, String signatureHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(rawPayload, signatureHeader, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException ex) {
            throw new InvalidWebhookSignatureException("Invalid Stripe signature");
        }

        if (webhookEventRepository.existsByStripeEventId(event.getId())) {
            log.info("Ignoring duplicate Stripe webhook event {}", event.getId());
            return;
        }
        webhookEventRepository.save(WebhookEvent.builder()
            .stripeEventId(event.getId())
            .eventType(event.getType())
            .build());

        StripePaymentRef ref = extractPaymentRef(event);
        if (ref == null || ref.paymentIntentId() == null) {
            log.debug("Ignoring webhook event {} - no PaymentIntent reference", event.getType());
            return;
        }

        // M-15: row-locked, so concurrent events for the same intent are applied one after another.
        Order order = orderRepository.findByStripePaymentIntentIdForUpdate(ref.paymentIntentId()).orElse(null);
        if (order != null) {
            applyStripeEvent(order, event.getType(), ref);
            return;
        }

        // Not an order payment - hand it to GiftCardService via an event (a direct call created a
        // PaymentService <-> GiftCardService circular dependency). Unknown intents are ignored there.
        // M-14: the refund details travel along, so a partial refund no longer voids the whole card.
        eventPublisher.publishEvent(new GiftCardStripeWebhookEvent(
            ref.paymentIntentId(), event.getType(),
            "charge.refunded".equals(event.getType()) ? ref.isFullRefund() : null,
            ref.majorUnits(ref.amountRefunded())
        ));
    }

    // ---------- Private helpers ----------

    // M-17: payment states after which no new charge may be taken. CANCELLED/REFUNDED/PARTIALLY_REFUNDED:
    // the order's money was already voided or returned (wallet funds included), so a later succeeded
    // webhook would mark a dead order PAID. Only PAID used to be checked.
    private static final Set<PaymentStatus> NOT_PAYABLE_STATUSES = EnumSet.of(
        PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED, PaymentStatus.CANCELLED
    );

    private static boolean isPayable(Order order) {
        return order.getOrderStatus() != OrderStatus.CANCELLED
            && !order.isStockReleased()
            && !NOT_PAYABLE_STATUSES.contains(order.getPaymentStatus());
    }

    private static void requirePayable(Order order) {
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new PaymentException("Order " + order.getId() + " is already fully paid — no payment intent needed");
        }
        // H-5: a cancelled or expired order has handed its stock back, so it must not be payable.
        // M-17: the same goes for an order whose payment was cancelled or refunded.
        if (!isPayable(order)) {
            throw new PaymentException("Order " + order.getId() + " was cancelled or refunded and can no longer be paid");
        }
    }

    // Best effort: if this fails the intent stays open, but it is not saved on the order, so
    // createPaymentIntent never hands it out again; Stripe cancels abandoned intents eventually.
    private void cancelIntentQuietly(String paymentIntentId) {
        try {
            stripeClient.paymentIntents().cancel(paymentIntentId);
        } catch (StripeException ex) {
            log.warn("Could not cancel PaymentIntent {} for an order that is no longer payable: {}",
                paymentIntentId, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    protected Order loadAndAuthorizeOrder(UUID orderId, UUID currentUserId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (!isAdmin && (order.getUser() == null || !order.getUser().getId().equals(currentUserId))) {
            throw new AccessDeniedException("Not your order");
        }
        return order;
    }

    @Transactional
    protected Order persistPaymentIntentId(UUID orderId, UUID userId, String paymentIntentId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // M-17: the caller cancels the new intent when the order is no longer payable; it must not be saved.
        if (order.getStripePaymentIntentId() == null && isPayable(order)) {
            order.setStripePaymentIntentId(paymentIntentId);
            orderRepository.save(order);

            eventPublisher.publishEvent(new PaymentStatusChangedEvent(
                order.getId(), userId,
                null,
                PaymentStatus.PENDING,
                "payment_intent.created"
            ));
        }
        return order;
    }

    public String ensureStripeCustomer(User user) {
        if (user.getStripeCustomerId() != null) {
            return user.getStripeCustomerId();
        }

        Customer customer;
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .setName(user.getFullName())
                .putMetadata("user_id", user.getId().toString())
                .build();

            RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey("stripe-customer-user-" + user.getId())
                .build();

            customer = stripeClient.customers().create(params, requestOptions);
        } catch (StripeException ex) {
            throw new PaymentException("Failed to create Stripe customer for user " + user.getId(), ex);
        }

        return self.persistStripeCustomerId(user.getId(), customer.getId());
    }

    @Transactional
    public String persistStripeCustomerId(UUID userId, String stripeCustomerId) {
        User lockedUser = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new PaymentException("User not found: " + userId));

        // Another concurrent call already created and persisted a customer — reuse it.
        if (lockedUser.getStripeCustomerId() != null) {
            return lockedUser.getStripeCustomerId();
        }

        lockedUser.setStripeCustomerId(stripeCustomerId);
        userRepository.save(lockedUser);
        return stripeCustomerId;
    }

    // What a webhook says about the payment it belongs to. Amounts are Stripe minor units (cents).
    // chargeAmount / amountRefunded / fullyRefunded: Charge events only (charge.refunded).
    // chargeAmount / disputeStatus: Dispute events only (charge.dispute.*).
    private record StripePaymentRef(
        String paymentIntentId, Long chargeAmount, Long amountRefunded,
        Boolean fullyRefunded, String currency, String disputeStatus
    ) {
        static StripePaymentRef of(String paymentIntentId) {
            return new StripePaymentRef(paymentIntentId, null, null, null, null, null);
        }

        // M-14: Stripe sends charge.refunded for partial refunds too. Charge.refunded is only true
        // once the whole charge is refunded; amount_refunded is the running total of all refunds.
        boolean isFullRefund() {
            if (Boolean.TRUE.equals(fullyRefunded)) return true;
            return chargeAmount != null && amountRefunded != null && amountRefunded >= chargeAmount;
        }

        BigDecimal majorUnits(Long minorUnits) {
            if (minorUnits == null || currency == null) return null;
            int digits = Currency.getInstance(currency.toUpperCase(Locale.ROOT)).getDefaultFractionDigits();
            return BigDecimal.valueOf(minorUnits).movePointLeft(digits).setScale(digits, RoundingMode.HALF_UP);
        }
    }

    private StripePaymentRef extractPaymentRef(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = deserializer.getObject().orElse(null);

        if (stripeObject == null) {
            log.warn("Could not deserialize Stripe event {} ({}) - likely API version mismatch; " +
                "falling back to raw JSON", event.getId(), event.getType());
            try {
                stripeObject = deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException ex) {
                log.warn("Fallback deserialization failed for event {} ({}): {}",
                    event.getId(), event.getType(), ex.getMessage());
                return null;
            }
        }

        if (stripeObject instanceof PaymentIntent intent) {
            return StripePaymentRef.of(intent.getId());
        }
        if (stripeObject instanceof Charge charge) {
            return new StripePaymentRef(charge.getPaymentIntent(), charge.getAmount(), charge.getAmountRefunded(),
                charge.getRefunded(), charge.getCurrency(), null);
        }
        // M-13: charge.dispute.* events carry a Dispute, not a Charge. This branch was missing, so the
        // method returned null and every chargeback was dropped as "no PaymentIntent reference".
        if (stripeObject instanceof Dispute dispute) {
            return new StripePaymentRef(dispute.getPaymentIntent(), dispute.getAmount(), null,
                null, dispute.getCurrency(), dispute.getStatus());
        }
        return null;
    }

    private void applyStripeEvent(Order order, String eventType, StripePaymentRef ref) {
        // M-13: how a disputed order is handled. A dispute being opened does NOT change the payment
        // status: the money is only held, and the salon can still win it with evidence in the Stripe
        // Dashboard (Stripe also emails the account owner). It is recorded for staff (audit entry) and
        // logged. Only a lost dispute moves the order on, to REFUNDED, via charge.dispute.closed below.
        // Requires charge.dispute.created and charge.dispute.closed to be enabled on the Stripe webhook.
        if ("charge.dispute.created".equals(eventType)) {
            log.warn("Stripe dispute opened on order {} ({} minor units, status {})",
                order.getOrderCode(), ref.chargeAmount(), ref.disputeStatus());
            UUID ownerId = order.getUser() != null ? order.getUser().getId() : null;
            eventPublisher.publishEvent(new OrderPaymentDisputedEvent(
                order.getId(), order.getOrderCode(), ownerId, ref.majorUnits(ref.chargeAmount()), ref.disputeStatus()));
            return;
        }

        PaymentStatus next = mapEventToStatus(eventType, ref);
        if (next == null) { log.debug("Unhandled Stripe event type: {}", eventType); return; }

        PaymentStatus current = order.getPaymentStatus();
        // M-14: a settled payment can still be refunded further: PAID -> PARTIALLY_REFUNDED -> REFUNDED.
        // A repeated partial refund (PARTIALLY_REFUNDED -> PARTIALLY_REFUNDED) is not a change.
        boolean isRefund = REFUNDABLE_STATUSES.contains(current)
            && (next == PaymentStatus.REFUNDED || next == PaymentStatus.PARTIALLY_REFUNDED)
            && next != current;

        if (TERMINAL_STATUSES.contains(current) && !isRefund) {
            log.info("Order {} payment already {}, ignoring late/out-of-order event -> {}",
                order.getId(), current, next);
            return;
        }

        // M-17: should no longer happen (createPaymentIntent refuses dead orders), but an intent created
        // before the order was cancelled can still be paid. The status records the truth (money was
        // taken); the log line is what tells staff to refund it.
        if (next == PaymentStatus.PAID && order.getOrderStatus() == OrderStatus.CANCELLED) {
            log.error("Order {} was paid after it was cancelled (intent {}). Refund it in the Stripe Dashboard.",
                order.getOrderCode(), ref.paymentIntentId());
        }

        order.setPaymentStatus(next);
        orderRepository.save(order);

        // Only a canceled intent is really dead. A failed one can still be retried and paid, so
        // refunding the wallet on FAILED would let the customer keep both the refund and the goods.
        // Listeners: GiftCardService restores the wallet, OrderService cancels the order and returns its stock (H-5).
        if (next == PaymentStatus.CANCELLED) {
            eventPublisher.publishEvent(new OrderPaymentVoidedEvent(order));
        }

        // Order.user is nullable. A 500 here would make Stripe retry the webhook forever.
        UUID ownerId = order.getUser() != null ? order.getUser().getId() : null;
        eventPublisher.publishEvent(new PaymentStatusChangedEvent(
            order.getId(), ownerId, current, next, eventType
        ));
    }

    private PaymentStatus mapEventToStatus(String eventType, StripePaymentRef ref) {
        return switch (eventType) {
            case "payment_intent.requires_action" -> PaymentStatus.REQUIRES_ACTION;
            case "payment_intent.processing" -> PaymentStatus.PROCESSED;
            case "payment_intent.succeeded" -> PaymentStatus.PAID;
            case "payment_intent.payment_failed" -> PaymentStatus.FAILED;
            case "payment_intent.canceled" -> PaymentStatus.CANCELLED;
            // M-14: was always REFUNDED, so refunding one item of a three-item order showed the
            // whole order as refunded.
            case "charge.refunded" -> ref.isFullRefund() ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
            // M-13: a lost dispute means Stripe took the money back. Won / warning_closed: nothing changes.
            case "charge.dispute.closed" -> "lost".equals(ref.disputeStatus()) ? PaymentStatus.REFUNDED : null;
            default -> null;
        };
    }

    private PaymentIntent retrieveExistingIntent(String stripePaymentIntentId) {
        try {
            return stripeClient.paymentIntents().retrieve(stripePaymentIntentId);
        } catch (StripeException ex) {
            throw new PaymentException("Failed to retrieve PaymentIntent " + stripePaymentIntentId, ex);
        }
    }

    @Retryable(
        retryFor = { ApiConnectionException.class, ApiException.class },
        backoff = @Backoff(delay = 500, multiplier = 2)
    )
    PaymentIntent createStripePaymentIntent(Order order, String stripeCustomerId) throws StripeException {
        BigDecimal wallet = order.getWalletAmountApplied() != null ? order.getWalletAmountApplied() : BigDecimal.ZERO;
        BigDecimal amountDue = order.getTotal().subtract(wallet);
        if (amountDue.signum() <= 0) {
            throw new PaymentException("Order " + order.getId() + " has nothing left to charge");
        }

        String currency = stripeConfig.getCurrency();
        // Same fraction-digit source as PaymentMapper.toMajorUnits, so the two
        // conversions stay inverse of each other for non-2-decimal currencies
        // (JPY has 0 minor digits, KWD/BHD have 3).
        int fractionDigits = Currency.getInstance(currency.toUpperCase(Locale.ROOT)).getDefaultFractionDigits();

        long minorUnits = amountDue
            .movePointRight(fractionDigits)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(minorUnits)
            .setCurrency(currency)
            .setCustomer(stripeCustomerId)
            .putMetadata("order_id", order.getId().toString())
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                    .setEnabled(true)
                    .build()
            )
            .build();

        RequestOptions options = RequestOptions.builder()
            .setIdempotencyKey("order_" + order.getId() + "_payment_intent")
            .build();

        return stripeClient.paymentIntents().create(params, options);
    }
}