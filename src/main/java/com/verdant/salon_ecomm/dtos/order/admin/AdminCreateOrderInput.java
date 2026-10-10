package com.verdant.salon_ecomm.dtos.order.admin;

import com.verdant.salon_ecomm.dtos.AddressInput;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

public record AdminCreateOrderInput(
    UUID userId,
    String fullName,
    String phone,
    String email,
    @Valid AddressInput shippingAddress,
    String paymentMethod,
    List<AdminOrderItemInput> items,
    String promoCode
) {}
