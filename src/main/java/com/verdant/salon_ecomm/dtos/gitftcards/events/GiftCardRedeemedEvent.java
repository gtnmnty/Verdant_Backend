package com.verdant.salon_ecomm.dtos.gitftcards.events;

import com.verdant.salon_ecomm.models.entities.giftcards.GiftCard;
import com.verdant.salon_ecomm.models.entities.User;

public record GiftCardRedeemedEvent(GiftCard giftCard, User redeemedBy) {}