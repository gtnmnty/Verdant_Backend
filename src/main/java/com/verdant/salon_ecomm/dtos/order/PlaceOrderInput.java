package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.dtos.AddressInput;

import java.util.List;
import java.util.UUID;

public record PlaceOrderInput(
    List<UUID> cartItemIds,
    AddressInput shippingAddress,
    String paymentMethod
) {}
