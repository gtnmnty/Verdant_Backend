package com.verdant.salon_ecomm.dtos.promo_code.events;

import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.entities.promo_code.PromoCodeRedemption;

public record PromoCodeAppliedToOrderEvent(
    Order order,
    User customer,
    PromoCodeRedemption redemption
) {}
