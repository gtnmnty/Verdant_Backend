package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.order.*;
import com.verdant.salon_ecomm.dtos.order.admin.AdminCreateOrderInput;
import com.verdant.salon_ecomm.dtos.order.admin.AdminOrderDto;
import com.verdant.salon_ecomm.dtos.order.admin.AdminOrderPage;
import com.verdant.salon_ecomm.dtos.order.admin.AdminUpdateOrderInput;
import com.verdant.salon_ecomm.entities.Address;
import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.mappers.OrderMapper;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import com.verdant.salon_ecomm.models.enums.orders.*;
import com.verdant.salon_ecomm.repositories.OrderItemRepository;
import com.verdant.salon_ecomm.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class OrderResolver {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final OrderItemRepository orderItemRepository;

    // ---------- Queries ----------

    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    public OrderPage myOrders(
        @Argument OrderClientFilter status,
        @Argument OrderTimeframe timeframe,
        @Argument String search,
        @Argument OrderClientSort sort,
        @Argument int page,
        @Argument int pageSize,
        @AuthenticationPrincipal User principal
    ) {
        return orderService.getMyOrders(principal.getId(), status, timeframe, search, sort, page, pageSize);
    }

    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    public Order order(@Argument UUID id, @AuthenticationPrincipal User principal) {
        boolean isAdmin = hasElevatedRole(principal);
        return orderService.getOrderById(id, principal.getId(), isAdmin);
    }

    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','ADMIN','OWNER')")
    @QueryMapping
    public AdminOrderPage adminOrders(
        @Argument OrderStatus status,
        @Argument String search,
        @Argument AdminOrderSort sort,
        @Argument OrderSortDirection direction,
        @Argument int page,
        @Argument int pageSize
    ) {
        return orderService.getAdminOrders(status, search, sort, direction, page, pageSize);
    }

    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','ADMIN','OWNER')")
    @QueryMapping
    public AdminOrderDto adminOrder(@Argument UUID id) {
        return orderService.getAdminOrderById(id);
    }

    // ---------- Mutations ----------

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    public Order placeOrder(@Argument("input") PlaceOrderInput input, @AuthenticationPrincipal User principal) {
        return orderService.placeOrder(principal.getId(), input);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    @MutationMapping
    public AdminOrderDto adminCreateOrder(
        @Argument("input") AdminCreateOrderInput input, @AuthenticationPrincipal User principal
    ) {
        return orderService.createAdminOrder(input, principal);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    @MutationMapping
    public AdminOrderDto adminUpdateOrder(
        @Argument UUID id, @Argument("input") AdminUpdateOrderInput input, @AuthenticationPrincipal User principal
    ) {
        return orderService.updateAdminOrder(id, input, principal);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    @MutationMapping
    public List<Order> adminDeleteOrders(@Argument List<UUID> ids, @AuthenticationPrincipal User principal) {
        return orderService.adminDeleteOrders(ids, principal);
    }

    // ---------- Field resolvers ----------

    @SchemaMapping(typeName = "Order", field = "items")
    public List<OrderItemDto> items(Order order) {
        return orderItemRepository.findByOrder_Id(order.getId()).stream()
            .map(orderMapper::toOrderItemDto)
            .toList();
    }

    @SchemaMapping(typeName = "Order", field = "address")
    public Address address(Order order) {
        return order.getShippingAddress();
    }

    private boolean hasElevatedRole(User principal) {
        AccountRole role = principal.getRole();
        return role == AccountRole.RECEPTIONIST
            || role == AccountRole.MANAGER
            || role == AccountRole.OWNER
            || role == AccountRole.ADMIN;
    }
}