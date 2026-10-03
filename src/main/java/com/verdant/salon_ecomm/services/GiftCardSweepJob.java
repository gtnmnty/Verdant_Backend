package com.verdant.salon_ecomm.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GiftCardSweepJob {

    private final GiftCardService giftCardService;

    @Scheduled(cron = "0 0 * * * *") // every hour, on the hour
    public void run() {
        try {
            int expired = giftCardService.sweepExpiredCards();
            if (expired > 0) log.info("Gift card sweep: expired {} cards", expired);
        } catch (Exception e) {
            log.error("Gift card expiry sweep failed", e);
        }
        try {
            int cancelled = giftCardService.cancelAbandonedPurchases();
            if (cancelled > 0) log.info("Gift card sweep: cancelled {} abandoned purchases", cancelled);
        } catch (Exception e) {
            log.error("Gift card abandoned-purchase sweep failed", e);
        }
    }
}
