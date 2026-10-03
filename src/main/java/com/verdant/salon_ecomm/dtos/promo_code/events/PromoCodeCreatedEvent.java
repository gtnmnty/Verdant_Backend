package com.verdant.salon_ecomm.dtos.promo_code.events;

import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.entities.promo_code.PromoCode;

public record PromoCodeCreatedEvent(PromoCode promoCode, User actor) {}
