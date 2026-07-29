package com.verdant.salon_ecomm.dtos.order.admin;

import java.util.List;

public record AdminOrderPage(
    List<AdminOrderDto> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
