package com.verdant.salon_ecomm.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// The in-memory window used while Redis is down: 0 = allowed, otherwise milliseconds until allowed.
class PublicFormRateLimiterFallbackTest {

    @Test
    void allowsUpToTheLimitThenReportsTheWait() {
        PublicFormRateLimiter.LocalWindow window = new PublicFormRateLimiter.LocalWindow();

        assertThat(window.tryAcquire("k", 2, 60_000, 1_000)).isZero();
        assertThat(window.tryAcquire("k", 2, 60_000, 5_000)).isZero();
        // Full: the hit at t=1000 leaves the window at t=61000.
        assertThat(window.tryAcquire("k", 2, 60_000, 10_000)).isEqualTo(51_000);
    }

    @Test
    void slotFreesOnceTheOldestHitLeavesTheWindow() {
        PublicFormRateLimiter.LocalWindow window = new PublicFormRateLimiter.LocalWindow();
        window.tryAcquire("k", 1, 60_000, 0);

        assertThat(window.tryAcquire("k", 1, 60_000, 59_999)).isEqualTo(1);
        assertThat(window.tryAcquire("k", 1, 60_000, 60_000)).isZero();
    }
}
