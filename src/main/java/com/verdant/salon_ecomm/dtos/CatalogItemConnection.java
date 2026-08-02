package com.verdant.salon_ecomm.dtos;

import java.util.List;

public record CatalogItemConnection(
    List<Object> items,
    PageInfo pageInfo
) {}
