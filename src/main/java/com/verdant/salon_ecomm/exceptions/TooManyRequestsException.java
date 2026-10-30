package com.verdant.salon_ecomm.exceptions;

// M-5: a rate limit rejected the request. Mapped to HTTP 429 (REST) and code TOO_MANY_REQUESTS (GraphQL).
// Used to be IllegalStateException, which surfaced as 409 Conflict and told the client nothing useful.
// retryAfterSeconds: when the limiter can tell, how long until the next attempt is allowed. Sent as the
// Retry-After header (REST) and extensions.retryAfter (GraphQL), so the frontend can count down exactly.
public class TooManyRequestsException extends RuntimeException {

    private final Long retryAfterSeconds;

    public TooManyRequestsException(String message) {
        this(message, null);
    }

    public TooManyRequestsException(String message, Long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds == null ? null : Math.max(1, retryAfterSeconds);
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    // Milliseconds left in a window, rounded up to whole seconds (never 0: "retry in 0s" would just fail again).
    public static long secondsFromMillis(long millis) {
        return Math.max(1, (millis + 999) / 1000);
    }
}
