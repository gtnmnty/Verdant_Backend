package com.verdant.salon_ecomm.dtos.order;

import java.util.List;

public record OrderPage(
    List<OrderDto> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
