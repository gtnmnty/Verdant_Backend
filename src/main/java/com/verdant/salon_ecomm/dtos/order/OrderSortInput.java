package com.verdant.salon_ecomm.dtos.order;

public record OrderSortInput(
        OrderSortField field,
        SortDirection direction
) {
    public OrderSortInput {
        if (field == null) {
            field = OrderSortField.CREATED_AT;
        }
        if (direction == null) {
            direction = field == OrderSortField.TOTAL
                    ? SortDirection.DESC
                    : SortDirection.DESC;
        }
    }

    public enum OrderSortField {
        CREATED_AT,
        TOTAL
    }

    public enum SortDirection {
        ASC,
        DESC
    }
}
