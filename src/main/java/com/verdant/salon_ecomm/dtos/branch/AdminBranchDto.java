package com.verdant.salon_ecomm.dtos.branch;

import com.verdant.salon_ecomm.dtos.AddressDto;
import com.verdant.salon_ecomm.entities.OperatingHours;
import com.verdant.salon_ecomm.models.enums.BranchStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminBranchDto(
    UUID id,
    String name,
    AddressDto address,
    String phone,
    String email,
    OperatingHours operatingHours,
    String googleMapsUrl,
    String imageUrl,
    BranchStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}

