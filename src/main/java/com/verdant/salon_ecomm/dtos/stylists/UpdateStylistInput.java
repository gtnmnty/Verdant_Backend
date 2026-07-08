package com.verdant.salon_ecomm.dtos.stylists;

import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.StylistAccountStatus;

import java.util.List;
import java.util.UUID;

public record UpdateStylistInput(
    UUID id,
    String avatarUrl,
    String fullName,
    String email,
    String phoneNumber,
    String bio,
    String branch,
    List<SalonService> offeredServices,
    StylistAccountStatus status
) {
}
