package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.entities.Order;

import java.util.List;

public record OrderPage(
    List<Order> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
