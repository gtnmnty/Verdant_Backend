package com.verdant.salon_ecomm.dtos.checkout;

import com.verdant.salon_ecomm.models.entities.Order;

// Exactly one is set: order when the gift-card wallet covered everything (created and paid immediately),
// checkout when a card payment is still needed (the order is created once Stripe confirms it).
public record PlaceOrderResult(Order order, CheckoutSessionDto checkout) {}
