package com.verdant.salon_ecomm.services;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles failed redeem attempts per user (guessing-attack protection).
 * In-memory => per instance. If you run several backend instances, back this with Redis or the DB.
 */
@Component
public class GiftCardRedeemLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final ConcurrentHashMap<UUID, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public void checkAllowed(UUID userId) {
        Deque<Instant> deque = failures.get(userId);
        if (deque == null) return;
        synchronized (deque) {
            prune(deque);
            if (deque.size() >= MAX_FAILURES) {
                throw new IllegalStateException("Too many invalid attempts. Please try again later.");
            }
        }
    }

    public void recordFailure(UUID userId) {
        Deque<Instant> deque = failures.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (deque) {
            prune(deque);
            deque.addLast(Instant.now());
        }
    }

    public void reset(UUID userId) {
        failures.remove(userId);
    }

    private void prune(Deque<Instant> deque) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) deque.removeFirst();
    }
}
