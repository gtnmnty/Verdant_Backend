package com.verdant.salon_ecomm.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class PublicFormRateLimiter {

    private static final String PREFIX = "rl:";

    // Atomic sliding window: purge old hits, reject if full, otherwise record this hit.
    private static final String LUA = """
        local key, now, window, max = KEYS[1], tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3])
        redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
        if redis.call('ZCARD', key) >= max then return 0 end
        redis.call('ZADD', key, now, ARGV[4])
        redis.call('PEXPIRE', key, window)
        return 1
        """;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA, Long.class);

    private final StringRedisTemplate redis;

    public PublicFormRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(String key, int maxAttempts, Duration window) {
        long now = System.currentTimeMillis();
        long allowed;
        try {
            allowed = redis.execute(
                SCRIPT,
                List.of(PREFIX + key),
                String.valueOf(now),
                String.valueOf(window.toMillis()),
                String.valueOf(maxAttempts),
                now + "-" + UUID.randomUUID()   // unique member so same-millisecond hits aren't merged
            );
        } catch (DataAccessException e) {
            log.error("Rate limiter unavailable, failing open", e);
            return; // see note below
        }
        if (allowed == 0L) {
            throw new IllegalStateException("Too many requests. Please try again later.");
        }
    }
}