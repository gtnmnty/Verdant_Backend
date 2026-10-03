package com.verdant.salon_ecomm.dtos.gitftcards.events;

import com.verdant.salon_ecomm.models.entities.giftcards.GiftCard;
import com.verdant.salon_ecomm.models.entities.User;

import java.math.BigDecimal;

// Fired on both expiry-forfeit and delete-account-refund flows.
public record GiftCardForfeitedEvent(
    GiftCard giftCard,
    User owner,
    BigDecimal forfeitedAmount,
    boolean refundIssued,
    GiftCard replacementCard // <- null unless refundIssued
) {}