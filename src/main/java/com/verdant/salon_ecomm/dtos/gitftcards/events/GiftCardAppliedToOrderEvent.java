package com.verdant.salon_ecomm.dtos.gitftcards.events;

import com.verdant.salon_ecomm.models.entities.GiftCardTransaction;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;

import java.math.BigDecimal;
import java.util.List;

public record GiftCardAppliedToOrderEvent(
    Order order,
    User customer,
    List<GiftCardTransaction> transactions,
    BigDecimal totalApplied
) {}