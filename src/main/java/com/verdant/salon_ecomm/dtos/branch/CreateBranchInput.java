package com.verdant.salon_ecomm.dtos.branch;

import com.verdant.salon_ecomm.models.enums.BranchStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record CreateBranchInput(
    @NotBlank(message = "Branch name is required")
    String name,
    BranchAddressInput address,
    String phone,
    String email,
    @Valid
    OperatingHoursInput operatingHours,
    String googleMapsUrl,
    String imageUrl,
    BranchStatus status
) {}
