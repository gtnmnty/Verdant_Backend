package com.verdant.salon_ecomm.dtos.gitftcards.events;

// Published by PaymentService for any Stripe PaymentIntent/Charge event that isn't an order payment.
// GiftCardService listens, so PaymentService no longer depends on
//          GiftCardService (breaks the circular bean dependency).
public record GiftCardStripeWebhookEvent(String paymentIntentId, String eventType) {}
