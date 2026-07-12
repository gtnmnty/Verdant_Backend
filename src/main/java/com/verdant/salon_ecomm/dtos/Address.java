package com.verdant.salon_ecomm.dtos;

public record Address(
    String line1,
    String line2,
    String city,
    String state,
    String postal,
    String country
) {}
