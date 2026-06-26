package com.verdant.salon_ecomm.dtos;

import java.time.OffsetDateTime;


public record ErrorResponse(
    OffsetDateTime timestamp,
    int status,
    String error,
    String message,
    String path
) {}

