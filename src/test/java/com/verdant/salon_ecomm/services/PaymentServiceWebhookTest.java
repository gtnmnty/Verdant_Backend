package com.verdant.salon_ecomm.services;

import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.verdant.salon_ecomm.config.StripeConfig;
import com.verdant.salon_ecomm.dtos.giftcards.events.GiftCardStripeWebhookEvent;
import com.verdant.salon_ecomm.dtos.giftcards.events.OrderPaymentVoidedEvent;
import com.verdant.salon_ecomm.dtos.payment.OrderPaymentDisputedEvent;
import com.verdant.salon_ecomm.dtos.payment.PaymentStatusChangedEvent;
import com.verdant.salon_ecomm.exceptions.InvalidWebhookSignatureException;
import com.verdant.salon_ecomm.mappers.PaymentMapper;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.entities.stripe.WebhookEvent;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import com.verdant.salon_ecomm.repositories.OrderRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.repositories.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// PaymentService.handleStripeWebhook end to end, minus the database: real Stripe signature verification and
// event parsing (payloads are signed here with a test secret), then the status-transition rules:
// terminal statuses ignore late events, PAID -> PARTIALLY_REFUNDED -> REFUNDED, disputes, duplicates,
// and hand-off of non-order intents to GiftCardService.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceWebhookTest {

    private static final String SECRET = "whsec_unit_test_secret";
    private static final String PI = "pi_test_123";

    @Mock OrderRepository orderRepository;
    @Mock UserRepository userRepository;
    @Mock WebhookEventRepository webhookEventRepository;
    @Mock PaymentMapper paymentMapper;
    @Mock StripeConfig stripeConfig;
    @Mock StripeClient stripeClient;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock PaymentService self;

    PaymentService service;
    int eventSeq;

    @BeforeEach
    void setUp() {
        service = new PaymentService(orderRepository, userRepository, webhookEventRepository, paymentMapper,
            stripeConfig, stripeClient, eventPublisher, self);
        when(stripeConfig.getWebhookSecret()).thenReturn(SECRET);
    }

    // ── helpers: signed Stripe payloads ──────────────────

    private void deliver(String eventId, String type, String dataObjectJson) {
        String payload = """
            {"id":"%s","object":"event","type":"%s","api_version":"%s","created":%d,
             "data":{"object":%s}}""".formatted(eventId, type, Stripe.API_VERSION,
            System.currentTimeMillis() / 1000, dataObjectJson);
        service.handleStripeWebhook(payload, sign(payload));
    }

    private void deliver(String type, String dataObjectJson) {
        deliver("evt_" + (++eventSeq), type, dataObjectJson);
    }

    private static String sign(String payload) {
        try {
            long t = System.currentTimeMillis() / 1000;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String sig = HexFormat.of().formatHex(mac.doFinal((t + "." + payload).getBytes(StandardCharsets.UTF_8)));
            return "t=" + t + ",v1=" + sig;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String intent() {
        return "{\"id\":\"" + PI + "\",\"object\":\"payment_intent\"}";
    }

    private static String charge(long amount, long refunded, boolean fullyRefunded) {
        return """
            {"id":"ch_1","object":"charge","payment_intent":"%s","amount":%d,"amount_refunded":%d,
             "refunded":%b,"currency":"usd"}""".formatted(PI, amount, refunded, fullyRefunded);
    }

    private static String dispute(String status) {
        return """
            {"id":"dp_1","object":"dispute","payment_intent":"%s","amount":10000,"currency":"usd","status":"%s"}"""
            .formatted(PI, status);
    }

    private Order order(PaymentStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderCode("ORD-1");
        order.setPaymentStatus(status);
        order.setOrderStatus(OrderStatus.PLACED);
        order.setStripePaymentIntentId(PI);
        order.setUser(User.builder().id(UUID.randomUUID()).build());
        when(orderRepository.findByStripePaymentIntentIdForUpdate(PI)).thenReturn(Optional.of(order));
        return order;
    }

    // ── signature and idempotency ────────────────────────

    @Test
    void badSignatureIsRejectedBeforeAnythingIsRecorded() {
        String payload = "{\"id\":\"evt_x\",\"object\":\"event\",\"type\":\"payment_intent.succeeded\"}";

        assertThatThrownBy(() -> service.handleStripeWebhook(payload, "t=1,v1=deadbeef"))
            .isInstanceOf(InvalidWebhookSignatureException.class);
        verifyNoInteractions(webhookEventRepository, orderRepository, eventPublisher);
    }

    @Test
    void eachEventIdIsRecordedAndADuplicateIsIgnored() {
        Order order = order(PaymentStatus.PENDING);
        when(webhookEventRepository.existsByStripeEventId("evt_dup")).thenReturn(false, true);

        deliver("evt_dup", "payment_intent.succeeded", intent());
        order.setPaymentStatus(PaymentStatus.PENDING);   // prove the 2nd delivery doesn't touch it
        deliver("evt_dup", "payment_intent.succeeded", intent());

        ArgumentCaptor<WebhookEvent> recorded = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventRepository, times(1)).save(recorded.capture());
        assertThat(recorded.getValue().getStripeEventId()).isEqualTo("evt_dup");
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    // ── status transitions ───────────────────────────────

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "payment_intent.requires_action, REQUIRES_ACTION",
        "payment_intent.processing,      PROCESSED",
        "payment_intent.succeeded,       PAID",
        "payment_intent.payment_failed,  FAILED",
        "payment_intent.canceled,        CANCELLED",
    })
    void intentEventsMoveAPendingOrder(String type, PaymentStatus expected) {
        Order order = order(PaymentStatus.PENDING);

        deliver(type, intent());

        assertThat(order.getPaymentStatus()).isEqualTo(expected);
        ArgumentCaptor<PaymentStatusChangedEvent> changed = ArgumentCaptor.forClass(PaymentStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(changed.capture());
        assertThat(changed.getValue().previousStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(changed.getValue().newStatus()).isEqualTo(expected);
    }

    @Test
    void failedPaymentCanStillSucceedLater() {
        Order order = order(PaymentStatus.FAILED);

        deliver("payment_intent.succeeded", intent());

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @ParameterizedTest(name = "{0} ignores {1}")
    @CsvSource({
        "PAID,      payment_intent.payment_failed",
        "PAID,      payment_intent.canceled",
        "PAID,      payment_intent.processing",
        "REFUNDED,  payment_intent.succeeded",
        "CANCELLED, payment_intent.succeeded",
        "PARTIALLY_REFUNDED, payment_intent.succeeded",
    })
    void terminalStatusesIgnoreLateOrOutOfOrderEvents(PaymentStatus current, String type) {
        Order order = order(current);

        deliver(type, intent());

        assertThat(order.getPaymentStatus()).isEqualTo(current);
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void onlyACanceledIntentVoidsTheOrderPayment() {
        Order canceled = order(PaymentStatus.PENDING);
        deliver("payment_intent.canceled", intent());
        verify(eventPublisher).publishEvent(any(OrderPaymentVoidedEvent.class));
        assertThat(canceled.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);

        reset(eventPublisher);
        order(PaymentStatus.PENDING);
        deliver("payment_intent.payment_failed", intent());   // retryable: wallet must not be refunded
        verify(eventPublisher, never()).publishEvent(any(OrderPaymentVoidedEvent.class));
    }

    // ── M-14 refunds ─────────────────────────────────────

    @Test
    void partialThenFullRefund() {
        Order order = order(PaymentStatus.PAID);

        deliver("charge.refunded", charge(10_000, 3_000, false));
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);

        deliver("charge.refunded", charge(10_000, 10_000, true));
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refundedTotalReachingTheChargeAmountCountsAsFullEvenWithoutTheFlag() {
        Order order = order(PaymentStatus.PAID);

        deliver("charge.refunded", charge(10_000, 10_000, false));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void repeatedPartialRefundIsNotAStatusChange() {
        Order order = order(PaymentStatus.PARTIALLY_REFUNDED);

        deliver("charge.refunded", charge(10_000, 5_000, false));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void refundedOrderNeverGoesBackToPartiallyRefunded() {
        Order order = order(PaymentStatus.REFUNDED);

        deliver("charge.refunded", charge(10_000, 3_000, false));   // older event arriving late

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    // ── M-13 disputes ────────────────────────────────────

    @Test
    void openedDisputeIsReportedButDoesNotChangeTheStatus() {
        Order order = order(PaymentStatus.PAID);

        deliver("charge.dispute.created", dispute("needs_response"));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        ArgumentCaptor<OrderPaymentDisputedEvent> disputed = ArgumentCaptor.forClass(OrderPaymentDisputedEvent.class);
        verify(eventPublisher).publishEvent(disputed.capture());
        assertThat(disputed.getValue().amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void lostDisputeRefundsWonDisputeChangesNothing() {
        Order won = order(PaymentStatus.PAID);
        deliver("charge.dispute.closed", dispute("won"));
        assertThat(won.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        Order lost = order(PaymentStatus.PAID);
        deliver("charge.dispute.closed", dispute("lost"));
        assertThat(lost.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    // ── not an order: hand-off to gift cards ─────────────

    @Test
    void intentWithoutAnOrderIsHandedToGiftCardsWithRefundDetailsInMajorUnits() {
        when(orderRepository.findByStripePaymentIntentIdForUpdate(PI)).thenReturn(Optional.empty());

        deliver("charge.refunded", charge(5_000, 1_250, false));

        ArgumentCaptor<GiftCardStripeWebhookEvent> handedOff = ArgumentCaptor.forClass(GiftCardStripeWebhookEvent.class);
        verify(eventPublisher).publishEvent(handedOff.capture());
        assertThat(handedOff.getValue().paymentIntentId()).isEqualTo(PI);
        assertThat(handedOff.getValue().fullRefund()).isFalse();
        assertThat(handedOff.getValue().amountRefunded()).isEqualByComparingTo("12.50");
    }

    @Test
    void nonRefundEventsCarryNoRefundDetails() {
        when(orderRepository.findByStripePaymentIntentIdForUpdate(PI)).thenReturn(Optional.empty());

        deliver("payment_intent.succeeded", intent());

        ArgumentCaptor<GiftCardStripeWebhookEvent> handedOff = ArgumentCaptor.forClass(GiftCardStripeWebhookEvent.class);
        verify(eventPublisher).publishEvent(handedOff.capture());
        assertThat(handedOff.getValue().fullRefund()).isNull();
        assertThat(handedOff.getValue().amountRefunded()).isNull();
    }
}
