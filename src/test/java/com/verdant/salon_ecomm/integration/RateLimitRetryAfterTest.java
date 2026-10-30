package com.verdant.salon_ecomm.integration;

import com.verdant.salon_ecomm.AbstractIntegrationTest;
import com.verdant.salon_ecomm.exceptions.TooManyRequestsException;
import com.verdant.salon_ecomm.services.GiftCardRedeemLimiter;
import com.verdant.salon_ecomm.services.LoginThrottle;
import com.verdant.salon_ecomm.services.PublicFormRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

// Every limiter tells the client how long to wait (Retry-After / extensions.retryAfter), against real Redis.
class RateLimitRetryAfterTest extends AbstractIntegrationTest {

    @Autowired PublicFormRateLimiter publicFormRateLimiter;
    @Autowired LoginThrottle loginThrottle;
    @Autowired GiftCardRedeemLimiter giftCardRedeemLimiter;

    @Test
    void publicFormLimitReportsTimeUntilTheOldestHitExpires() {
        String key = "test:" + UUID.randomUUID();
        Duration window = Duration.ofMinutes(10);
        for (int i = 0; i < 3; i++) publicFormRateLimiter.check(key, 3, window);

        TooManyRequestsException ex = catchThrowableOfType(TooManyRequestsException.class,
            () -> publicFormRateLimiter.check(key, 3, window));

        // The first hit was a moment ago, so the wait is just under the full window.
        assertThat(ex.getRetryAfterSeconds()).isBetween(590L, 600L);
    }

    @Test
    void loginThrottleReportsTheRemainingBlock() {
        String email = UUID.randomUUID() + "@x.com";
        // 3 free failures, then the 10th and later block for 60 s; 4 failures -> a 1 s block.
        for (int i = 0; i < 10; i++) loginThrottle.recordFailure(email);

        TooManyRequestsException ex = catchThrowableOfType(TooManyRequestsException.class,
            () -> loginThrottle.checkAllowed(email));

        assertThat(ex.getRetryAfterSeconds()).isBetween(55L, 60L);
    }

    @Test
    void loginThrottleAllowsWhenNotBlocked() {
        loginThrottle.checkAllowed(UUID.randomUUID() + "@x.com");   // no exception
    }

    @Test
    void giftCardRedeemLimitReportsTimeUntilAFailureExpires() {
        UUID user = UUID.randomUUID();
        for (int i = 0; i < 5; i++) giftCardRedeemLimiter.recordFailure(user);

        TooManyRequestsException ex = catchThrowableOfType(TooManyRequestsException.class,
            () -> giftCardRedeemLimiter.checkAllowed(user));

        assertThat(ex.getRetryAfterSeconds()).isBetween(14 * 60L, 15 * 60L);
    }
}
