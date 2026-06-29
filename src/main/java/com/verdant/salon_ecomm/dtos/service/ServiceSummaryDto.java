package com.verdant.salon_ecomm.dtos.service;

import java.math.BigDecimal;

public record ServiceSummaryDto(
    String id,
    String name,
    String subName,
    String category,
    BigDecimal price,
    int durationMinutes,
    String primaryImageUrl,
    String badge,
    boolean isFeatured,
    boolean isHomeService
){}
