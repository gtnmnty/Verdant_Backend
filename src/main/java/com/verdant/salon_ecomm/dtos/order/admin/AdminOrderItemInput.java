package com.verdant.salon_ecomm.dtos.order.admin;

import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

// M-16: quantity was a plain int with no constraint. Changing a line from 2 to -5 put 7 units back on the
// shelf and gave the order a negative subtotal. Checked by OrderService's method validation
// (@Validated class + @Valid parameters), which cascades into each item through the @Valid list below.
public record AdminOrderItemInput(
    @NotNull(message = "Product is required") UUID productId,
    @NotNull(message = "Quantity is required") @Positive(message = "Quantity must be at least 1") Integer quantity,
    @NotNull(message = "Delivery option is required") DeliveryOption deliveryOption
) {}
