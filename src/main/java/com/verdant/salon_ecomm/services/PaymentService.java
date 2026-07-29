package com.verdant.salon_ecomm.services;

import com.stripe.StripeClient;
import com.stripe.exception.*;
import com.stripe.model.*;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.verdant.salon_ecomm.config.StripeConfig;
import com.verdant.salon_ecomm.dtos.payment.CreatePaymentInput;
import com.verdant.salon_ecomm.dtos.payment.PaymentIntentDto;
import com.verdant.salon_ecomm.dtos.payment.PaymentStatusChangedEvent;
import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.entities.stripe.WebhookEvent;
import com.verdant.salon_ecomm.exceptions.InvalidWebhookSignatureException;
import com.verdant.salon_ecomm.exceptions.PaymentException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.PaymentMapper;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
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

    private static final Set<PaymentStatus> TERMINAL_STATUSES = EnumSet.of(
        PaymentStatus.PAID, PaymentStatus.FAILED, PaymentStatus.REFUNDED, PaymentStatus.CANCELLED
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
            throw new PaymentException("Failed to create PaymentIntent for order " + order.getId());
        }

        Order updated = self.persistPaymentIntentId(order.getId(), intent.getId());
        if (!intent.getId().equals(updated.getStripePaymentIntentId())) {
            // Lost the race — another request already persisted its intent id first.
            return paymentMapper.toDto(retrieveExistingIntent(updated.getStripePaymentIntentId()));
        }

        eventPublisher.publishEvent(new PaymentStatusChangedEvent(
            order.getId(), order.getUser().getId(),
            null,              // no previous status — this is the initiation
            PaymentStatus.PENDING,         // or whatever your "awaiting payment" status is
            "payment_intent.created"       // not a real Stripe event type, just your own marker
        ));

        return paymentMapper.toDto(intent);
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

        String paymentIntentId = extractPaymentIntentId(event);
        if (paymentIntentId == null) {
            log.debug("Ignoring webhook event {} - no PaymentIntent reference", event.getType());
            return;
        }

        Order order = orderRepository.findByStripePaymentIntentId(paymentIntentId).orElse(null);
        if (order == null) {
            log.warn("Received webhook for unknown PaymentIntent {}", paymentIntentId);
            return;
        }

        applyStripeEvent(order, event.getType());
    }

    // ---------- Private helpers ----------

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
    protected Order persistPaymentIntentId(UUID orderId, String paymentIntentId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStripePaymentIntentId() == null) {
            order.setStripePaymentIntentId(paymentIntentId);
            orderRepository.save(order);
        }
        return order;
    }

    protected String ensureStripeCustomer(User user) {
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

    private String extractPaymentIntentId(Event event) {
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
            return intent.getId();
        }
        if (stripeObject instanceof Charge charge) {
            return charge.getPaymentIntent();
        }
        return null;
    }

    private void applyStripeEvent(Order order, String eventType) {
        PaymentStatus next = mapEventToStatus(eventType);
        if (next == null) { log.debug("Unhandled Stripe event type: {}", eventType); return; }

        PaymentStatus current = order.getPaymentStatus();
        boolean isRefund = current == PaymentStatus.PAID && next == PaymentStatus.REFUNDED;

        if (TERMINAL_STATUSES.contains(current) && !isRefund) {
            log.info("Order {} payment already {}, ignoring late/out-of-order event -> {}",
                order.getId(), current, next);
            return;
        }

        order.setPaymentStatus(next);
        orderRepository.save(order);

        eventPublisher.publishEvent(new PaymentStatusChangedEvent(
            order.getId(), order.getUser().getId(), current, next, eventType
        ));
    }

    private PaymentStatus mapEventToStatus(String eventType) {
        return switch (eventType) {
            case "payment_intent.requires_action" -> PaymentStatus.REQUIRES_ACTION;
            case "payment_intent.processing" -> PaymentStatus.PROCESSED;
            case "payment_intent.succeeded" -> PaymentStatus.PAID;
            case "payment_intent.payment_failed" -> PaymentStatus.FAILED;
            case "payment_intent.canceled" -> PaymentStatus.CANCELLED;
            case "charge.refunded" -> PaymentStatus.REFUNDED;
            default -> null;
        };
    }

    private PaymentIntent retrieveExistingIntent(String stripePaymentIntentId) {
        try {
            return stripeClient.paymentIntents().retrieve(stripePaymentIntentId);
        } catch (StripeException ex) {
            throw new PaymentException("Failed to create Stripe customer for user " + stripePaymentIntentId, ex);
        }
    }

    @Retryable(
        retryFor = { ApiConnectionException.class, ApiException.class },
        backoff = @Backoff(delay = 500, multiplier = 2)
    )
    PaymentIntent createStripePaymentIntent(Order order, String stripeCustomerId) throws StripeException {
        BigDecimal orderTotal = order.getTotal();
        if (orderTotal == null || orderTotal.signum() <= 0) {
            throw new PaymentException("Order " + order.getId() + " has no valid total to charge");
        }

        String currency = stripeConfig.getCurrency();
        // Same fraction-digit source as PaymentMapper.toMajorUnits, so the two
        // conversions stay inverse of each other for non-2-decimal currencies
        // (JPY has 0 minor digits, KWD/BHD have 3).
        int fractionDigits = Currency.getInstance(currency.toUpperCase(Locale.ROOT)).getDefaultFractionDigits();

        long minorUnits = orderTotal
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