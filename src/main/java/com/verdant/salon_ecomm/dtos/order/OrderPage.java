package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.models.entities.Order;

import java.util.List;

// Holds entities, not OrderDto (H-12): the Order field resolvers in OrderResolver (items, address)
// take the entity as their source, and every other query returning "Order" already passes entities.
public record OrderPage(
    List<Order> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
