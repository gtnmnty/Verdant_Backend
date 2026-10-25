package com.verdant.salon_ecomm.models.enums;

public enum PaymentStatus {
    PENDING,
    REQUIRES_ACTION,
    PROCESSED,
    PAID,
    FAILED,
    // M-14: part of a paid charge was refunded (Stripe charge.refunded with amount_refunded < amount).
    // Still counts as paid; a later refund can move it on to REFUNDED.
    // Keep in sync with enum PaymentStatus in graphql/order.graphql and the CHECK constraints in V8.
    PARTIALLY_REFUNDED,
    REFUNDED,
    CANCELLED;
}
