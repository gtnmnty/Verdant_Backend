package com.verdant.salon_ecomm.dtos.giftcards.events;

import java.math.BigDecimal;

// Published by PaymentService for any Stripe PaymentIntent/Charge/Dispute event that isn't an order payment.
// GiftCardService listens, so PaymentService no longer depends on
//          GiftCardService (breaks the circular bean dependency).
// M-14: fullRefund / amountRefunded are only set for charge.refunded (null otherwise), so a partial
// refund can take part of the balance instead of voiding the whole card.
// amountRefunded is Stripe's running total for the charge (all refunds so far), in the store currency.
public record GiftCardStripeWebhookEvent(
    String paymentIntentId,
    String eventType,
    Boolean fullRefund,
    BigDecimal amountRefunded
) {}
