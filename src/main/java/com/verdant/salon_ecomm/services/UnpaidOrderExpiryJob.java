package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.models.entities.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UnpaidOrderExpiryJob {
    // PlaceOrder reserves stock before payment. Orders left unpaid past the TTL are canceled
    // and their stock (and any wallet money) returned, so abandoned checkouts can't drain inventory.
    // Scheduling is switched on in config/SchedulingConfig (H-8).
    private static final int BATCH_SIZE = 50;

    private final OrderService orderService;
    private final PaymentService paymentService;

    // How long an unpaid order may hold its stock. Long enough for 3-D Secure and a slow customer, short
    // because these leftovers are hidden from the customer's order list (see OrderSpec.paymentAttempted).
    @Value("${app.orders.unpaid-ttl-minutes:20}")
    private long unpaidTtlMinutes;

    @Scheduled(fixedDelayString = "${app.orders.unpaid-expiry-interval-ms:300000}") // default: every 5 min
    public void run() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(unpaidTtlMinutes);
        List<Order> candidates;
        try {
            candidates = orderService.findExpiredUnpaidOrders(cutoff, BATCH_SIZE);
        } catch (Exception e) {
            log.error("Unpaid-order expiry: could not list candidates", e);
            return;
        }

        int expired = 0;
        for (Order order : candidates) {
            try {
                // Stripe first, outside any DB transaction: if the intent can't be canceled (it was
                // just paid, or is processing), the order is left alone and the webhook settles it.
                String intentId = order.getStripePaymentIntentId();
                if (intentId != null && !paymentService.cancelPaymentIntentForExpiry(intentId)) {
                    continue;
                }
                // Own transaction with a row lock and a re-check, so a payment that landed meanwhile wins.
                if (orderService.expireUnpaidOrder(order.getId())) expired++;
            } catch (Exception e) {
                // One bad order must not stop the batch; it is retried on the next run.
                log.error("Unpaid-order expiry failed for order {}", order.getId(), e);
            }
        }
        if (expired > 0) log.info("Unpaid-order expiry: cancelled {} orders and returned their stock", expired);
    }
}
