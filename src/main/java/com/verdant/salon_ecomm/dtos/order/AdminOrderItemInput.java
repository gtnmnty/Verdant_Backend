package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.models.enums.DeliveryOption;

import java.util.UUID;

public record AdminOrderItemInput(
    UUID productId,
    int quantity,
    DeliveryOption deliveryOption
) {}
