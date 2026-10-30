package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PublicFormRateLimiter {

    private static final String PREFIX = "rl:";

    // Atomic sliding window: purge old hits, reject if full, otherwise record this hit.
    // Returns 1 when allowed; when rejected, minus the milliseconds until the oldest hit leaves the window
    // (at most -1), which becomes the Retry-After.
    private static final String LUA = """
        local key, now, window, max = KEYS[1], tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3])
        redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
        if redis.call('ZCARD', key) >= max then
            local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
            local wait = window
            if oldest[2] then wait = tonumber(oldest[2]) + window - now end
            if wait < 1 then wait = 1 end
            return -wait
        end
        redis.call('ZADD', key, now, ARGV[4])
        redis.call('PEXPIRE', key, window)
        return 1
        """;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA, Long.class);

    private final StringRedisTemplate redis;
    private final LocalWindow fallback = new LocalWindow();

    public PublicFormRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // M-5 flow: Redis sliding window -> on a Redis error, the same window kept in this JVM's memory
    // -> over the limit: TooManyRequestsException (HTTP 429 / GraphQL TOO_MANY_REQUESTS).
    // Explicit decision for a Redis outage: neither fail open (the old behaviour: no protection at all,
    // so login could be brute-forced and signup could mail-bomb) nor fail closed (nobody could sign in
    // until Redis is back). The local fallback keeps the limits per instance, so with N instances an
    // attacker gets up to N times the limit during the outage, which is still bounded.
    public void check(String key, int maxAttempts, Duration window) {
        long now = System.currentTimeMillis();
        boolean allowed;
        long retryAfterMs;
        try {
            Long result = redis.execute(
                SCRIPT,
                List.of(PREFIX + key),
                String.valueOf(now),
                String.valueOf(window.toMillis()),
                String.valueOf(maxAttempts),
                now + "-" + UUID.randomUUID()   // unique member so same-millisecond hits aren't merged
            );
            allowed = result != null && result == 1L;
            retryAfterMs = result != null && result < 0 ? -result : window.toMillis();
        } catch (DataAccessException e) {
            log.warn("Rate limiter: Redis unavailable, using the in-memory fallback: {}", e.getMessage());
            long waitMs = fallback.tryAcquire(key, maxAttempts, window.toMillis(), now);
            allowed = waitMs == 0;
            retryAfterMs = waitMs;
        }
        if (!allowed) {
            throw new TooManyRequestsException("Too many requests. Please try again later.",
                TooManyRequestsException.secondsFromMillis(retryAfterMs));
        }
    }

    // Per-instance sliding window, only used while Redis is down.
    // Bounded: once MAX_KEYS keys are tracked, expired ones are dropped, and if every tracked key is
    // still active a NEW key is refused (fails closed). Without the cap, a flood of distinct keys
    // (random emails) during an outage would grow the map until the JVM ran out of memory.
    static final class LocalWindow {
        private static final int MAX_KEYS = 10_000;

        private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
        private final Map<String, Long> windows = new ConcurrentHashMap<>();

        // 0 = allowed (and recorded); otherwise the milliseconds until an attempt would be allowed.
        long tryAcquire(String key, int maxAttempts, long windowMs, long now) {
            if (!hits.containsKey(key) && hits.size() >= MAX_KEYS) {
                evictExpired(now);
                if (hits.size() >= MAX_KEYS) return windowMs;
            }
            windows.put(key, windowMs);
            Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (deque) {
                while (!deque.isEmpty() && deque.peekFirst() <= now - windowMs) deque.pollFirst();
                if (deque.size() >= maxAttempts) return Math.max(1, deque.peekFirst() + windowMs - now);
                deque.addLast(now);
                return 0;
            }
        }

        private void evictExpired(long now) {
            hits.entrySet().removeIf(entry -> {
                long windowMs = windows.getOrDefault(entry.getKey(), 0L);
                Deque<Long> deque = entry.getValue();
                synchronized (deque) {
                    boolean expired = deque.isEmpty() || deque.peekLast() <= now - windowMs;
                    if (expired) windows.remove(entry.getKey());
                    return expired;
                }
            });
        }
    }
}
