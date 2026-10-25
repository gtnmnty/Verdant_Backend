package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

// M-5: per-account brute-force protection for /auth/login, replacing the hard "3 tries per email per
// 15 minutes" lockout that let anyone lock a chosen user out with three bad passwords.
//
// Flow (called from AuthenticationService.authenticate):
//   checkAllowed(email)   -> 429 only while a short block from earlier failures is running
//   wrong password        -> recordFailure(email): count++, and from the 4th failure on a block of
//                            1s, 2s, 4s ... capped at 60s
//   correct password      -> reset(email)
// An attacker can now hold an account for at most a minute per failed try, and each try counts
// against their own per-IP limit in AuthController. The real user waits seconds, not 15 minutes.
//
// Redis down: fails open (logged). The per-IP limit in PublicFormRateLimiter still applies, with its
// in-memory fallback, so login keeps working and is still capped.
@Slf4j
@Component
public class LoginThrottle {

    private static final String FAILS_PREFIX = "login-fails:";
    private static final String BLOCK_PREFIX = "login-block:";

    // First failures are free: typos shouldn't slow anyone down.
    private static final int FREE_FAILURES = 3;
    private static final long MAX_DELAY_SECONDS = 60;
    // A failure is forgotten after this long without another one.
    private static final Duration FAILURE_MEMORY = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    public LoginThrottle(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void checkAllowed(String email) {
        boolean blocked;
        try {
            blocked = Boolean.TRUE.equals(redis.hasKey(BLOCK_PREFIX + key(email)));
        } catch (DataAccessException e) {
            log.warn("Login throttle unavailable, failing open: {}", e.getMessage());
            return;
        }
        if (blocked) {
            throw new TooManyRequestsException("Too many failed sign-in attempts. Wait a moment and try again.");
        }
    }

    public void recordFailure(String email) {
        String k = key(email);
        try {
            Long failures = redis.opsForValue().increment(FAILS_PREFIX + k);
            redis.expire(FAILS_PREFIX + k, FAILURE_MEMORY);
            if (failures != null && failures > FREE_FAILURES) {
                // 4th failure -> 1s, 5th -> 2s, 6th -> 4s ... 10th and later -> 60s.
                long exponent = Math.min(failures - FREE_FAILURES - 1, 6);
                long delaySeconds = Math.min(1L << exponent, MAX_DELAY_SECONDS);
                redis.opsForValue().set(BLOCK_PREFIX + k, "1", Duration.ofSeconds(delaySeconds));
            }
        } catch (DataAccessException e) {
            log.warn("Login throttle unavailable, failure not recorded: {}", e.getMessage());
        }
    }

    public void reset(String email) {
        String k = key(email);
        try {
            redis.delete(List.of(FAILS_PREFIX + k, BLOCK_PREFIX + k));
        } catch (DataAccessException e) {
            log.warn("Login throttle unavailable, could not reset: {}", e.getMessage());
        }
    }

    // "Foo@x.com " and "foo@x.com" share one counter.
    private static String key(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
