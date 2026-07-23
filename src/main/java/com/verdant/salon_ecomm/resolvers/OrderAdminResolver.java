package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.order.OrderDetailDto;
import com.verdant.salon_ecomm.dtos.order.OrderFilterInput;
import com.verdant.salon_ecomm.dtos.order.OrderSummaryDto;
import com.verdant.salon_ecomm.dtos.order.PageDto;
import com.verdant.salon_ecomm.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.UUID;

/**
 * Resolves order-management queries for higher roles (images 3 & 4):
 * OWNER, ADMIN, MANAGER, RECEPTIONIST. STYLIST is intentionally excluded
 * — adjust if stylists should also see the orders table.
 */
@Controller
@RequiredArgsConstructor
public class OrderAdminResolver {

    private final OrderService orderService;

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER', 'RECEPTIONIST')")
    @QueryMapping
    public PageDto<OrderSummaryDto> orders(@Argument OrderFilterInput filter) {
        OrderFilterInput effective = filter == null
                ? new OrderFilterInput(null, null, 0, 10, null)
                : filter;
        return orderService.getAllOrders(effective);
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER', 'RECEPTIONIST')")
    @QueryMapping
    public OrderDetailDto order(@Argument UUID orderId) {
        return orderService.getOrderDetail(orderId);
    }
}
