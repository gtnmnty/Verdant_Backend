package com.verdant.salon_ecomm.dtos.branch;

import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.models.enums.BranchStatus;
import jakarta.validation.constraints.NotBlank;

public record CreateBranchInput(
    @NotBlank(message = "Branch name is required")
    String name,
    AddressInput address,
    String phone,

    @NotBlank(message = "Email is required")
    String email,

    @NotBlank(message = "Branch name is required")
    OperatingHoursInput openingHours,

    String googleMapsUrl,
    String imageUrl,
    BranchStatus status
) {}
