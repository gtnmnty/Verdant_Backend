package com.verdant.salon_ecomm.dtos.reviews;

import java.util.UUID;

public record ReviewUserDto(
        UUID id,
        String fullName
) {}