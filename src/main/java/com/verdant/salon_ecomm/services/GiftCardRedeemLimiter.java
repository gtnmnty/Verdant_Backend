package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Throttles failed redeem attempts per user (guessing-attack protection).
 * Backed by the same Redis as PublicFormRateLimiter, so the limit is shared across instances
 * and each user's key expires on its own (nothing accumulates in memory).
 * Only failures count; a successful redeem clears the user's record.
 */
@Slf4j
@Component
public class GiftCardRedeemLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String PREFIX = "rl:giftcard-redeem:";

    private final StringRedisTemplate redis;

    public GiftCardRedeemLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void checkAllowed(UUID userId) {
        String key = PREFIX + userId;
        long now = System.currentTimeMillis();
        Long failures;
        try {
            redis.opsForZSet().removeRangeByScore(key, 0, now - WINDOW.toMillis());
            failures = redis.opsForZSet().zCard(key);
        } catch (DataAccessException e) {
            // Same policy as PublicFormRateLimiter: an outage must not block redemptions.
            log.error("Gift card redeem limiter unavailable, failing open", e);
            return;
        }
        if (failures != null && failures >= MAX_FAILURES) {
            // M-5: 429 like the other limits (was IllegalStateException -> "Something went wrong").
            throw new TooManyRequestsException("Too many invalid attempts. Please try again later.");
        }
    }

    public void recordFailure(UUID userId) {
        String key = PREFIX + userId;
        long now = System.currentTimeMillis();
        try {
            // Unique member so same-millisecond failures aren't merged.
            redis.opsForZSet().add(key, now + "-" + UUID.randomUUID(), now);
            redis.expire(key, WINDOW);
        } catch (DataAccessException e) {
            log.error("Gift card redeem limiter unavailable, failure not recorded", e);
        }
    }

    public void reset(UUID userId) {
        try {
            redis.delete(PREFIX + userId);
        } catch (DataAccessException e) {
            log.error("Gift card redeem limiter unavailable, could not reset user {}", userId, e);
        }
    }
}
