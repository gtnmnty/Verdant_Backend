package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.dtos.AddressInput;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

public record PlaceOrderInput(
    List<UUID> cartItemIds,
    @Valid AddressInput shippingAddress,
    String paymentMethod,
    Boolean useWalletBalance,
    String promoCode
) {}
