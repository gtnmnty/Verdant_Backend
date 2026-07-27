package com.verdant.salon_ecomm.dtos.order;

import java.util.UUID;

public record AdminOrderUserDto(
    UUID id,
    String fullName,
    String email,
    String phone
) {}
