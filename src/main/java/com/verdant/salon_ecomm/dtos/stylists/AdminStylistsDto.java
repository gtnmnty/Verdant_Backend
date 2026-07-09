package com.verdant.salon_ecomm.dtos.stylists;

import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.StylistAccountStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminStylistsDto(
    String id,
    String name,
    String email,
    String phone,
    String avatarUrl,
    String bio,
    BranchDto branch,
    List<SalonService> services,
    StylistAccountStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}