package com.verdant.salon_ecomm.dtos.stylists;

import com.verdant.salon_ecomm.models.enums.StylistAccountStatus;

import java.util.List;
import java.util.UUID;

public record UpdateStylistInput(
    UUID id,
    String name,
    String email,
    String phone,
    String bio,
    String avatarUrl,
    String branchId,
    List<String> serviceIds,
    StylistAccountStatus status
) {
}