package com.verdant.salon_ecomm.exceptions;

// M-5: a rate limit rejected the request. Mapped to HTTP 429 (REST) and code TOO_MANY_REQUESTS (GraphQL).
// Used to be IllegalStateException, which surfaced as 409 Conflict and told the client nothing useful.
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
