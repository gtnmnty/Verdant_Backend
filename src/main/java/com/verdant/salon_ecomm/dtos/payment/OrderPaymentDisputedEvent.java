package com.verdant.salon_ecomm.dtos.payment;

import java.math.BigDecimal;
import java.util.UUID;

// M-13: Stripe opened a dispute (chargeback) on an order's payment. Published by PaymentService on
// charge.dispute.created; the payment status is left as is (the dispute may still be won).
// PaymentAuditListener records it so staff see it in the audit log.
// amount: disputed amount in the store currency, null if Stripe didn't send one.
public record OrderPaymentDisputedEvent(
    UUID orderId,
    String orderCode,
    UUID ownerId,
    BigDecimal amount,
    String disputeStatus
) {}
