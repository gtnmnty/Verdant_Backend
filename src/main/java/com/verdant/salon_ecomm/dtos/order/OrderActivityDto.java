package com.verdant.salon_ecomm.dtos.order;

import java.time.OffsetDateTime;

public record OrderActivityDto(
        String label,
        OffsetDateTime occurredAt
) {
}
