package com.verdant.salon_ecomm.dtos.order.admin;

import com.verdant.salon_ecomm.dtos.AddressInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record AdminCreateOrderInput(
    UUID userId,
    String fullName,
    String phone,
    String email,
    @Valid AddressInput shippingAddress,
    String paymentMethod,
    // M-16: same per-line checks as AdminUpdateOrderInput.items (positive quantity, ids present).
    @Valid @NotEmpty(message = "At least one item is required") List<AdminOrderItemInput> items,
    String promoCode
) {}
