package com.verdant.salon_ecomm.dtos.order;

import com.verdant.salon_ecomm.models.enums.OrderStatus;

public record OrderFilterInput(
        String search,
        OrderStatus status,
        Integer page,
        Integer size,
        OrderSortInput sort
) {
    public OrderFilterInput {
        page = (page == null || page < 0) ? 0 : page;
        size = (size == null || size <= 0) ? 10 : size;
        sort = (sort == null) ? new OrderSortInput(null, null) : sort;
    }
}
