package com.verdant.salon_ecomm.dtos.stylists;

import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.StylistAccountStatus;

import java.util.List;

public record CreateStylistInput(
    String avatarUrl,
    String fullName,
    String email,
    String phoneNumber,
    String bio,
    String branch,
    List<SalonService> offeredServices,
    StylistAccountStatus status
) {}
