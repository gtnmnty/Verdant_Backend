package com.verdant.salon_ecomm.dtos;

public record AddressDto(
    String line1,
    String line2,
    String city,
    String postal,
    String state,
    String country
) {}