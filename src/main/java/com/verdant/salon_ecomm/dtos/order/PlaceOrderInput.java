package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.dtos.AddressInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PlaceOrderInput(
    List<UUID> cartItemIds,
    // An order is never saved without contact details; stored on the order, not on the customer's profile.
    @NotBlank(message = "Full name is required") @Size(max = 100, message = "Full name must be at most 100 characters")
    String fullName,
    @NotBlank(message = "Phone number is required") @Size(max = 20, message = "Phone number must be at most 20 characters")
    String phone,
    @NotBlank(message = "Email is required") @Email(message = "Email is not valid")
    @Size(max = 255, message = "Email must be at most 255 characters")
    String email,
    @Valid AddressInput shippingAddress,
    String paymentMethod,
    Boolean useWalletBalance,
    String promoCode
) {}
