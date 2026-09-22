package com.verdant.salon_ecomm.dtos.branch;

import com.verdant.salon_ecomm.entities.OperatingHours;
import com.verdant.salon_ecomm.models.enums.BranchStatus;

import java.util.UUID;

public record BranchDto(
    UUID id,
    String name,
    BranchAddressDto address,
    String phone,
    String email,
    OperatingHours operatingHours,
    String googleMapsUrl,
    String imageUrl,
    BranchStatus status
) {}
