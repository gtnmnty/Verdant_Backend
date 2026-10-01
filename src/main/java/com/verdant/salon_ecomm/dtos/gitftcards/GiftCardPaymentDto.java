package com.verdant.salon_ecomm.dtos.gitftcards;

public record GiftCardPaymentDto(
    GiftCardDto giftCard,
    String clientSecret
) {}
