package com.verdant.salon_ecomm.dtos.reviews;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewDto(
        UUID id,
        ReviewUserDto user,
        short stars,
        String text,
        OffsetDateTime createdAt
) {}