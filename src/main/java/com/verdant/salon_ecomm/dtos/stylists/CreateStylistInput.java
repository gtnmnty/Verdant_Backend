package com.verdant.salon_ecomm.dtos.stylists;

import com.verdant.salon_ecomm.models.enums.stylists.StylistAccountStatus;

import java.util.List;

public record CreateStylistInput(
    String avatarUrl,
    String name,
    String email,
    String phone,
    String bio,
    String branchId,
    List<String> serviceIds,
    StylistAccountStatus status
) {}
