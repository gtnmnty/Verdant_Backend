package com.verdant.salon_ecomm.services;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sliding-window limiter for public (no-login) forms. Counts EVERY call, successful or not, so the
 * answer never depends on whether an email exists.
 * In-memory => per backend instance. With several instances behind a load balancer, move this to Redis.
 */
@Component
public class PublicFormRateLimiter {

    private static final int SWEEP_EVERY = 500; // calls between clean-ups of idle keys

    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();

    public void check(String key, int maxAttempts, Duration window) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);

        Deque<Instant> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) deque.removeFirst();
            if (deque.size() >= maxAttempts) {
                throw new IllegalStateException("Too many requests. Please try again later.");
            }
            deque.addLast(now);
        }

        if (calls.incrementAndGet() % SWEEP_EVERY == 0) sweep();
    }

    /** Drops keys with no recent hits so the map can't grow forever. 1h covers the longest window used. */
    private void sweep() {
        Instant idleBefore = Instant.now().minus(Duration.ofHours(1));
        hits.entrySet().removeIf(e -> {
            Deque<Instant> d = e.getValue();
            synchronized (d) {
                return d.isEmpty() || d.peekLast().isBefore(idleBefore);
            }
        });
    }
}
