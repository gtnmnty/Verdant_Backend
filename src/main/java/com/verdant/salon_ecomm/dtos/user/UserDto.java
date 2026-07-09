package com.verdant.salon_ecomm.dtos.user;


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
        String phone,
        String role,
        boolean emailVerified,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record Admin(
        UUID id,
        String fullName,
        String email,
        String phoneNumber,
        String role,
        boolean emailVerified,
        boolean active,
        Instant createdAt,
        Instant lastLoginAt
    ) {}

    public record AuthPayload(
        String accessToken,
        String refreshToken,
        long expiresIn,     // seconds
        Profile user
    ) {}
}