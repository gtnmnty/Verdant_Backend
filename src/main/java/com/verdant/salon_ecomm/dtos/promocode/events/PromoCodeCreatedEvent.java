package com.verdant.salon_ecomm.dtos.promocode.events;

import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.entities.promocode.PromoCode;

public record PromoCodeCreatedEvent(PromoCode promoCode, User actor) {}
