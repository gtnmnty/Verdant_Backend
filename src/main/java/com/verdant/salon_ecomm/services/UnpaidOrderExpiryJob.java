package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.PendingCheckout;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

// Cleans up what customers abandon. Scheduling is switched on in config/SchedulingConfig (H-8).
//
// 1. Draft checkouts past expires_at: a draft holds no stock, so this only cancels its Stripe intent and deletes
//    the row. A draft whose payment can't be cancelled was paid (or is processing): its order is created instead,
//    in case the webhook missed it.
// 2. Legacy unpaid ORDERS from before orders were written only once paid. They still hold reserved stock, which
//    is returned here. No new rows reach this state.
@Component
@RequiredArgsConstructor
@Slf4j
public class UnpaidOrderExpiryJob {
    private static final int BATCH_SIZE = 50;

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final CheckoutService checkoutService;

    // How long a legacy unpaid order may hold its stock.
    @Value("${app.orders.unpaid-ttl-minutes:20}")
    private long unpaidTtlMinutes;

    @Scheduled(fixedDelayString = "${app.orders.unpaid-expiry-interval-ms:300000}") // default: every 5 min
    public void run() {
        expireDrafts();
        expireLegacyUnpaidOrders();
    }

    private void expireDrafts() {
        List<PendingCheckout> expired;
        try {
            expired = orderService.findExpiredDrafts(OffsetDateTime.now(), BATCH_SIZE);
        } catch (Exception e) {
            log.error("Draft expiry: could not list expired drafts", e);
            return;
        }

        int purged = 0;
        for (PendingCheckout draft : expired) {
            try {
                String intentId = draft.getStripePaymentIntentId();
                // Stripe first, outside any DB transaction: once canceled, the customer can no longer pay it.
                if (intentId == null || paymentService.cancelPaymentIntentForExpiry(intentId)) {
                    orderService.discardDraft(draft.getId());
                    purged++;
                } else {
                    checkoutService.settleExpiredDraft(draft); // paid or processing: make the order
                }
            } catch (Exception e) {
                // One bad draft must not stop the batch; it is retried on the next run.
                log.error("Draft expiry failed for checkout {}", draft.getId(), e);
            }
        }
        if (purged > 0) log.info("Draft expiry: removed {} abandoned checkouts", purged);
    }

    private void expireLegacyUnpaidOrders() {
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
