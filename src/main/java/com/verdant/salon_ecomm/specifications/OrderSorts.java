package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.dtos.order.OrderSortInput;
import org.springframework.data.domain.Sort;

public final class OrderSorts {

    private OrderSorts() {
    }

    public static Sort toSort(OrderSortInput sortInput) {
        OrderSortInput effective = sortInput == null ? new OrderSortInput(null, null) : sortInput;

        String property = switch (effective.field()) {
            case TOTAL -> "total";
            case CREATED_AT -> "createdAt";
        };

        Sort.Direction direction = effective.direction() == OrderSortInput.SortDirection.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        return Sort.by(direction, property);
    }
}
