package com.verdant.salon_ecomm.models.enums.giftcards;

public enum GiftCardStatus {
    ACTIVE,     // issued / paid, waiting to be redeemed
    REDEEMED,   // owned by a user, spendable while balance > 0
    DEPLETED,   // fully spent (was CANCELLED before - CANCELLED now means voided/refunded/abandoned)
    EXPIRED,
    CANCELLED
}