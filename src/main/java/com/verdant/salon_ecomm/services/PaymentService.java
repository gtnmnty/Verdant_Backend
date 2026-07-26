package com.verdant.salon_ecomm.services;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.verdant.salon_ecomm.config.StripeConfig;
import com.verdant.salon_ecomm.dtos.payment.CreatePaymentInput;
import com.verdant.salon_ecomm.dtos.payment.PaymentIntentDto;
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
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    // ASSUMPTION: Order has getPaymentStatus()/setPaymentStatus(PaymentStatus),
    // separate from its fulfillment `status` field, since you built a
    // dedicated PaymentStatus enum rather than reusing OrderStatus.
    private static final Set<PaymentStatus> TERMINAL_STATUSES = EnumSet.of(
        PaymentStatus.PAID, PaymentStatus.FAILED, PaymentStatus.REFUNDED, PaymentStatus.CANCELED
    );

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentMapper paymentMapper;
    private final StripeConfig stripeConfig;

    // ---------- Mutations ----------

    @Transactional
    public PaymentIntentDto createPaymentIntent(CreatePaymentInput input, UUID currentUserId, boolean isAdmin){
        Order order = orderRepository.findById(input.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + input.orderId()));

        if (!isAdmin && (order.getUser() == null || !order.getUser().getId().equals(currentUserId))) {
            throw new AccessDeniedException("Not your order");
        }

        if (order.getStripePaymentIntentId() != null) {
            return paymentMapper.toDto(retrieveExistingIntent(order.getStripePaymentIntentId()));
        }

        User user = order.getUser();
        String stripeCustomerId = ensureStripeCustomer(user);

        PaymentIntent intent;
        try {
            intent = createStripePaymentIntent(order, stripeCustomerId);
        } catch (StripeException ex) {
            throw new PaymentException("Failed to create PaymentIntent for order " + order.getId(), ex);
        }

        order.setStripePaymentIntentId(intent.getId());
        orderRepository.save(order);

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

    private String extractPaymentIntentId(Event event) {
        StripeObject stripeObject = event.getDataObjectDeserializer().getObject().orElse(null);
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
        if (next == null) {
            log.debug("Unhandled Stripe event type: {}", eventType);
            return;
        }

        PaymentStatus current = order.getPaymentStatus();

        // Refund is the one legal transition OUT of a terminal state.
        boolean isRefund = current == PaymentStatus.PAID && next == PaymentStatus.REFUNDED;

        if (TERMINAL_STATUSES.contains(current) && !isRefund) {
            log.info("Order {} payment already {}, ignoring late/out-of-order event -> {}",
                order.getId(), current, next);
            return;
        }

        order.setPaymentStatus(next);
        orderRepository.save(order);
    }

    private PaymentStatus mapEventToStatus(String eventType) {
        return switch (eventType) {
            case "payment_intent.requires_action" -> PaymentStatus.REQUIRES_ACTION;
            case "payment_intent.processing" -> PaymentStatus.PROCESSED;
            case "payment_intent.succeeded" -> PaymentStatus.PAID;
            case "payment_intent.payment_failed" -> PaymentStatus.FAILED;
            case "payment_intent.canceled" -> PaymentStatus.CANCELED;
            case "charge.refunded" -> PaymentStatus.REFUNDED;
            default -> null;
        };
    }

    private String ensureStripeCustomer(User user) {
        if (user.getStripeCustomerId() != null) {
            return user.getStripeCustomerId();
        }
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .setName(user.getFullName())
                .putMetadata("user_id", user.getId().toString())
                .build();
            Customer customer = Customer.create(params);
            user.setStripeCustomerId(customer.getId());
            userRepository.save(user);
            return customer.getId();
        } catch (StripeException ex) {
            throw new PaymentException("Failed to create Stripe customer for user " + user.getId(), ex);
        }
    }

    private PaymentIntent retrieveExistingIntent(String stripePaymentIntentId) {
        try {
            return PaymentIntent.retrieve(stripePaymentIntentId);
        } catch (StripeException ex) {
            throw new PaymentException("Failed to retrieve existing PaymentIntent " + stripePaymentIntentId, ex);
        }
    }

    @Retryable(
        retryFor = { ApiConnectionException.class, ApiException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 500, multiplier = 2)
    )
    PaymentIntent createStripePaymentIntent(Order order, String stripeCustomerId) throws StripeException {
        long minorUnits = order.getTotal()
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(minorUnits)
            .setCurrency("usd")
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

        return PaymentIntent.create(params, options);
    }
}
