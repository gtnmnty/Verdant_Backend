package com.verdant.salon_ecomm.dtos.gitftcards.events;

import com.verdant.salon_ecomm.models.entities.GiftCard;
import com.verdant.salon_ecomm.models.entities.User;

public record GiftCardIssuedByAdminEvent(GiftCard giftCard, User actor) {}
