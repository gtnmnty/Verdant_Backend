package com.verdant.salon_ecomm.exceptions;

// A draft checkout was paid but can't become an order (an item sold out, the gift-card balance changed,
// the charge doesn't match the draft). Thrown inside the order-creation transaction so everything rolls back;
// CheckoutService then refunds the payment and discards the draft. The message is ours and safe to show.
public class CheckoutFailedException extends RuntimeException {
    public CheckoutFailedException(String message) {
        super(message);
    }
}
