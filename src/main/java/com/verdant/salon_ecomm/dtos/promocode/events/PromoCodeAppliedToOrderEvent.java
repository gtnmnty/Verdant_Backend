package com.verdant.salon_ecomm.dtos.promocode.events;

import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.entities.promocode.PromoCodeRedemption;

public record PromoCodeAppliedToOrderEvent(
    Order order,
    User customer,
    PromoCodeRedemption redemption
) {}
