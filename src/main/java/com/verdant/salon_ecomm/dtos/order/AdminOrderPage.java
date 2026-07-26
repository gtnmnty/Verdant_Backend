package com.verdant.salon_ecomm.dtos.order;

import java.util.List;

public record AdminOrderPage(
    List<AdminOrderDto> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
