package com.verdant.salon_ecomm.dtos.branch;

public record BranchAddressDto(
    String line1,
    String line2,
    String city,
    String postal,
    String state,
    String country
) {}