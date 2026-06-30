package com.verdant.salon_ecomm.dtos.product;

import com.verdant.salon_ecomm.entities.Product;

import java.util.List;

public record ProductPage(
    List<Product> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
