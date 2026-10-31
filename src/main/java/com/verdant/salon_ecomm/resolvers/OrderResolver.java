package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.order.OrderItemDto;
import com.verdant.salon_ecomm.dtos.order.OrderPage;
import com.verdant.salon_ecomm.dtos.order.PlaceOrderInput;
import com.verdant.salon_ecomm.dtos.order.admin.AdminCreateOrderInput;
import com.verdant.salon_ecomm.dtos.order.admin.AdminOrderDto;
import com.verdant.salon_ecomm.dtos.order.admin.AdminOrderPage;
import com.verdant.salon_ecomm.dtos.order.admin.AdminUpdateOrderInput;
import com.verdant.salon_ecomm.mappers.OrderMapper;
import com.verdant.salon_ecomm.models.entities.Address;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.orders.*;
import com.verdant.salon_ecomm.repositories.OrderItemRepository;
import com.verdant.salon_ecomm.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.verdant.salon_ecomm.security.SecurityHelper;

@Controller
@RequiredArgsConstructor
public class OrderResolver {

    private final OrderService orderService;
    private final SecurityHelper securityHelper;
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
        boolean isAdmin = securityHelper.isStaff(principal);
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

    // H-12: every query/mutation that returns the GraphQL type "Order" (myOrders, order, placeOrder,
    // adminDeleteOrders) now hands back the Order ENTITY, so these field resolvers always get the
    // source type they declare. Before, myOrders returned OrderDto, which couldn't bind to an Order
    // parameter, so items/address failed on every row of the customer's order history.
    //
    // @BatchMapping instead of @SchemaMapping: graphql-java collects every Order in the response
    // first, then calls this once with all of them. A page of 10 orders costs one item query, not 10.
    // Tradeoff: GraphQL serializes entity getters directly for this type (as order(id) already did).
    // The schema decides what's exposed, so internal fields like stripePaymentIntentId stay hidden,
    // but renaming an entity field now renames what the schema resolves unless a mapping is added.
    @BatchMapping(typeName = "Order", field = "items")
    public Map<Order, List<OrderItemDto>> items(List<Order> orders) {
        List<UUID> orderIds = orders.stream().map(Order::getId).toList();
        // The delivery estimate counts from when the order was placed; taken from the orders already in
        // hand so the lazy item.getOrder() proxy isn't loaded just for a date.
        Map<UUID, OffsetDateTime> placedAtByOrderId = orders.stream()
            .filter(order -> order.getCreatedAt() != null)
            .collect(Collectors.toMap(Order::getId, Order::getCreatedAt, (a, b) -> a));

        Map<UUID, List<OrderItemDto>> itemsByOrderId = orderItemRepository.findByOrder_IdIn(orderIds).stream()
            .collect(Collectors.groupingBy(
                item -> item.getOrder().getId(),
                Collectors.mapping(
                    item -> orderMapper.toOrderItemDto(item, placedAtByOrderId.get(item.getOrder().getId())),
                    Collectors.toList()
                )
            ));

        // Every order gets an entry (empty list if it has no items): the field is non-null.
        Map<Order, List<OrderItemDto>> result = new LinkedHashMap<>();
        for (Order order : orders) {
            result.put(order, itemsByOrderId.getOrDefault(order.getId(), List.of()));
        }
        return result;
    }

    @SchemaMapping(typeName = "Order", field = "address")
    public Address address(Order order) {
        return order.getShippingAddress();
    }
}
