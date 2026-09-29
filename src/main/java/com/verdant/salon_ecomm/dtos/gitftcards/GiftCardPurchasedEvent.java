package com.verdant.salon_ecomm.dtos.gitftcards;

import com.verdant.salon_ecomm.models.entities.GiftCard;
import com.verdant.salon_ecomm.models.entities.User;

public record GiftCardPurchasedEvent(GiftCard giftCard, User purchaser) {}
