package com.verdant.salon_ecomm.dtos.user;

import com.verdant.salon_ecomm.models.entities.Address;

import java.time.Instant;
import java.util.UUID;

public class UserDto {

    public record Summary(
        UUID id,
        String fullName,
        String email,
        String phoneNumber
    ) {}

    public record Profile(
        UUID id,
        String fullName,
        String email,
        // Address awaiting code confirmation, null when no change is in progress.
        String pendingEmail,
        String phone,
        Address shippingAddress,
        String avatarUrl,
        String role,
        boolean emailVerified,
        Instant createdAt,
        Instant updatedAt
    ) {}
}
