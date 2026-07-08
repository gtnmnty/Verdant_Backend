package com.verdant.salon_ecomm.dtos.stylists;

import com.verdant.salon_ecomm.models.enums.StylistAccountStatus;
import com.verdant.salon_ecomm.models.enums.StylistAvailability;

public record StylistSummaryDto(
    String id,
    String name,
    String avatarUrl,
    StylistAccountStatus status
) {
}
