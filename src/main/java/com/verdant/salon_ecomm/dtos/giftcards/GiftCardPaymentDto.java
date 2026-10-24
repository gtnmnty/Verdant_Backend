package com.verdant.salon_ecomm.dtos.giftcards;

public record GiftCardPaymentDto(
    GiftCardDto giftCard,
    String clientSecret
) {}
