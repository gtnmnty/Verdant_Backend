package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.order.OrderDetailDto;
import com.verdant.salon_ecomm.dtos.order.OrderSortInput;
import com.verdant.salon_ecomm.dtos.order.PageDto;
import com.verdant.salon_ecomm.dtos.order.OrderSummaryDto;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

/**
 * Resolves the "My Orders" queries for the CUSTOMER role (images 1 & 2),
 * including the Newest First / Oldest First / Highest Total sort
 * dropdown from image 1.
 *
 * User implements UserDetails and is the Spring Security principal
 * directly (see JwtAuthenticationFilter), so it's injected here via
 * @AuthenticationPrincipal — no separate current-user helper needed.
 */
@Controller
@RequiredArgsConstructor
public class OrderClientResolver {

    private final OrderService orderService;

    @PreAuthorize("hasRole('CUSTOMER')")
    @QueryMapping
    public PageDto<OrderSummaryDto> myOrders(
            @AuthenticationPrincipal User currentUser,
            @Argument Integer page,
            @Argument Integer size,
            @Argument OrderSortInput sort) {
        return orderService.getMyRecentOrders(
                currentUser.getId(),
                page == null ? 0 : page,
                size == null ? 10 : size,
                sort);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @QueryMapping
    public PageDto<OrderSummaryDto> myOlderOrders(
            @AuthenticationPrincipal User currentUser,
            @Argument Integer page,
            @Argument Integer size,
            @Argument OrderSortInput sort) {
        return orderService.getMyOlderOrders(
                currentUser.getId(),
                page == null ? 0 : page,
                size == null ? 10 : size,
                sort);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @QueryMapping
    public OrderDetailDto myOrder(
            @AuthenticationPrincipal User currentUser,
            @Argument java.util.UUID orderId) {
        return orderService.getMyOrderDetail(currentUser.getId(), orderId);
    }
}
