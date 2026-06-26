package com.verdant.salon_ecomm.exceptions;

public class RefreshTokenExpiredException extends RuntimeException {
    /**
     * Creates an exception indicating that a refresh token has expired.
     *
     * @param message the detail message
     */
    public RefreshTokenExpiredException(String message) {
        super(message);
    }
}
