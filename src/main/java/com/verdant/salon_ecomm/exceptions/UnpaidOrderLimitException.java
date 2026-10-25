package com.verdant.salon_ecomm.exceptions;

// Thrown when a customer already has too many unpaid orders holding stock (H-5).
public class UnpaidOrderLimitException extends RuntimeException {
    public UnpaidOrderLimitException(String message) {
        super(message);
    }
}
