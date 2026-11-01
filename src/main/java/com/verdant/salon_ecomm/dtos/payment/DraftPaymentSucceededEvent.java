package com.verdant.salon_ecomm.dtos.payment;

// Published by the Stripe webhook for a succeeded payment that matches no order. CheckoutService turns the
// matching draft checkout (if there is one) into an order.
public record DraftPaymentSucceededEvent(String paymentIntentId) {}
