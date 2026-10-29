package com.verdant.salon_ecomm.integration;

import com.stripe.Stripe;
import com.verdant.salon_ecomm.AbstractIntegrationTest;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.repositories.OrderRepository;
import com.verdant.salon_ecomm.services.PaymentService;
import com.verdant.salon_ecomm.support.Concurrently;
import com.verdant.salon_ecomm.support.Concurrently.Outcome;
import com.verdant.salon_ecomm.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Webhooks against the real schema: every event id is stored once (unique stripe_event_id), redelivery and
// out-of-order delivery leave the order in the right state, and parallel duplicates can't apply twice.
class StripeWebhookTest extends AbstractIntegrationTest {

    @Autowired PaymentService paymentService;
    @Autowired OrderRepository orderRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;
    @Value("${stripe.webhook-secret}") String webhookSecret;

    private String signed(String payload) {
        try {
            long t = System.currentTimeMillis() / 1000;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "t=" + t + ",v1=" + HexFormat.of().formatHex(
                mac.doFinal((t + "." + payload).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void deliver(String eventId, String type, String object) {
        String payload = """
            {"id":"%s","object":"event","type":"%s","api_version":"%s","created":%d,"data":{"object":%s}}"""
            .formatted(eventId, type, Stripe.API_VERSION, System.currentTimeMillis() / 1000, object);
        paymentService.handleStripeWebhook(payload, signed(payload));
    }

    private static String intent(String pi) {
        return "{\"id\":\"" + pi + "\",\"object\":\"payment_intent\"}";
    }

    private static String charge(String pi, long amount, long refunded) {
        return """
            {"id":"ch_%s","object":"charge","payment_intent":"%s","amount":%d,"amount_refunded":%d,
             "refunded":%b,"currency":"usd"}""".formatted(UUID.randomUUID(), pi, amount, refunded, refunded >= amount);
    }

    private static String evt() {
        return "evt_" + UUID.randomUUID();
    }

    private Order newOrder(String pi) {
        User user = data.customer();
        return data.orderAwaitingPayment(user, pi);
    }

    private PaymentStatus statusOf(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow().getPaymentStatus();
    }

    private int storedEvents(String eventId) {
        return jdbc.queryForObject("select count(*) from stripe_webhook_events where stripe_event_id = ?",
            Integer.class, eventId);
    }

    @Test
    void redeliveredEventIsStoredOnceAndAppliedOnce() {
        String pi = "pi_" + UUID.randomUUID();
        Order order = newOrder(pi);
        String eventId = evt();

        deliver(eventId, "payment_intent.succeeded", intent(pi));
        deliver(eventId, "payment_intent.succeeded", intent(pi));

        assertThat(statusOf(order)).isEqualTo(PaymentStatus.PAID);
        assertThat(storedEvents(eventId)).isEqualTo(1);
    }

    @Test
    void parallelDuplicateDeliveriesCannotApplyTwice() throws Exception {
        String pi = "pi_" + UUID.randomUUID();
        Order order = newOrder(pi);
        String eventId = evt();

        List<Outcome<Object>> outcomes = Concurrently.run(4, i -> () -> {
            deliver(eventId, "payment_intent.succeeded", intent(pi));
            return null;
        });

        // The ones that lose the race on the unique stripe_event_id fail (Stripe retries them, and the
        // retry is then ignored as a duplicate). Nothing else may go wrong.
        assertThat(Concurrently.successes(outcomes)).isGreaterThanOrEqualTo(1);
        assertThat(outcomes).filteredOn(o -> !o.succeeded())
            .allSatisfy(o -> assertThat(o.error()).isInstanceOf(DataIntegrityViolationException.class));
        assertThat(storedEvents(eventId)).isEqualTo(1);
        assertThat(statusOf(order)).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void lateFailureAfterSuccessKeepsTheOrderPaid() {
        String pi = "pi_" + UUID.randomUUID();
        Order order = newOrder(pi);

        deliver(evt(), "payment_intent.succeeded", intent(pi));
        deliver(evt(), "payment_intent.payment_failed", intent(pi));
        deliver(evt(), "payment_intent.processing", intent(pi));

        assertThat(statusOf(order)).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void refundsOnlyMoveForwardEvenWhenDeliveredOutOfOrder() {
        String pi = "pi_" + UUID.randomUUID();
        Order order = newOrder(pi);

        deliver(evt(), "payment_intent.succeeded", intent(pi));
        deliver(evt(), "charge.refunded", charge(pi, 10_000, 10_000));   // full refund arrives first
        deliver(evt(), "charge.refunded", charge(pi, 10_000, 3_000));    // the earlier partial, late

        assertThat(statusOf(order)).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void parallelEventsForOneOrderAreAppliedOneAtATime() throws Exception {
        String pi = "pi_" + UUID.randomUUID();
        Order order = newOrder(pi);
        deliver(evt(), "payment_intent.succeeded", intent(pi));

        // A partial and a full refund racing: the row lock serializes them, so whatever the order of
        // arrival the end state is REFUNDED (REFUNDED never goes back to PARTIALLY_REFUNDED).
        List<Outcome<Object>> outcomes = Concurrently.run(2, i -> () -> {
            deliver(evt(), "charge.refunded", charge(pi, 10_000, i == 0 ? 4_000 : 10_000));
            return null;
        });

        assertThat(Concurrently.successes(outcomes)).isEqualTo(2);
        assertThat(statusOf(order)).isEqualTo(PaymentStatus.REFUNDED);
    }
}
