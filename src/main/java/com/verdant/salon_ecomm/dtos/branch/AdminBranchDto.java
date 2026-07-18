package com.verdant.salon_ecomm.dtos.branch;

import com.verdant.salon_ecomm.models.enums.BranchStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminBranchDto(
     UUID id,
     String name,
     String address,
     String phone,
     String email,
     String operatingHours,
     String googleMapsUrl,
     String imageUrl,
     BranchStatus status,
     OffsetDateTime createdAt,
     OffsetDateTime updatedAt
) {}

