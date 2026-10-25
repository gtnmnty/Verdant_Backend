package com.verdant.salon_ecomm.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// M-9: a rule the request broke, with a message written for the end user ("Maximum of 4 images
// allowed", "This promo code has expired"). GlobalExceptionHandler passes the message through as is,
// so never put internals (ids of other users, SQL, class names) in it.
// Plain IllegalArgumentException / IllegalStateException are still treated as internal: the JDK and
// libraries throw them too (UUID.fromString, Enum.valueOf), and their messages can leak details.
// Flow: service throws -> GlobalExceptionHandler -> GraphQL error (extensions.code / status) or REST ErrorResponse.
@Getter
public class BusinessRuleException extends RuntimeException {

    // Machine-readable, for the frontend to branch on (extensions.code in GraphQL).
    private final String code;
    private final HttpStatus status;

    public BusinessRuleException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    // Bad input: a missing, malformed or out-of-range value. 400.
    public static BusinessRuleException invalid(String message) {
        return new BusinessRuleException("INVALID_ARGUMENT", message, HttpStatus.BAD_REQUEST);
    }

    // Valid input, but the current state forbids it (already sent, limit reached, wrong status). 409.
    public static BusinessRuleException conflict(String message) {
        return new BusinessRuleException("BUSINESS_RULE", message, HttpStatus.CONFLICT);
    }
}
