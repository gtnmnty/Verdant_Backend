package com.verdant.salon_ecomm.dtos.giftcards.events;

import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.giftcards.GiftCard;

import java.math.BigDecimal;

// M-12: an order was cancelled, but the wallet money it used came from a gift card that has since been
// voided (CANCELLED, or its purchase refunded / charged back). The money was NOT put back on the card.
// GiftCardAuditListener records it so an admin can review it and settle the customer by hand.
public record GiftCardRestoreBlockedEvent(GiftCard giftCard, Order order, BigDecimal amount) {}
