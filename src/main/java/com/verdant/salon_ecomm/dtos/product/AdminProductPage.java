package com.verdant.salon_ecomm.dtos.product;

import java.util.List;

public record AdminProductPage(
      List<AdminProductDto> items,
      int page,
      int pageSize,
      int totalItems,
      int totalPages
) {}
