package com.verdant.salon_ecomm.dtos.branch;

import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.models.enums.BranchStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;

import java.util.UUID;

public record UpdateBranchInput(

    UUID id,
    String name,

    @Valid
    AddressInput address,
    String phone,

    @Email(message = "Email must be valid")
    String email,

    OperatingHoursInput openingHours,
    String googleMapsUrl,
    String imageUrl,
    BranchStatus status
) {}
