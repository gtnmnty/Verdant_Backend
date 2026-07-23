package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.order.OrderDetailDto;
import com.verdant.salon_ecomm.dtos.order.OrderFilterInput;
import com.verdant.salon_ecomm.dtos.order.OrderSortInput;
import com.verdant.salon_ecomm.dtos.order.OrderSummaryDto;
import com.verdant.salon_ecomm.dtos.order.PageDto;
import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.entities.OrderItem;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.OrderMapper;
import com.verdant.salon_ecomm.repositories.OrderItemRepository;
import com.verdant.salon_ecomm.repositories.OrderRepository;
import com.verdant.salon_ecomm.specifications.OrderSorts;
import com.verdant.salon_ecomm.specifications.OrderSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Concrete service backing the Orders module — no separate interface,
 * per request. Wraps OrderRepository/OrderItemRepository + OrderMapper
 * + the spec/sort helpers built for the GraphQL layer
 * (OrderClientResolver / OrderAdminResolver).
 *
 * NOTE: Order has no @OneToMany back-reference to OrderItem, so items
 * are fetched separately via OrderItemRepository. List views (My
 * Orders, admin table) batch-fetch all items for the page in a single
 * findByOrderIdIn(...) query and group them in memory, rather than
 * querying per order.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    /**
     * Cutoff for "recent" vs "older" orders on the customer My Orders
     * page (images 1 & 2): anything placed in the last 30 days is
     * "recent", anything before that is reached via "View Older History".
     */
    private static final int RECENT_ORDER_WINDOW_DAYS = 30;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;

    /**
     * Customer "My Orders" — default view, orders < 30 days old (image 1).
     * 10 per page. `sort` backs the Newest/Oldest/Highest Total dropdown.
     */
    public PageDto<OrderSummaryDto> getMyRecentOrders(UUID userId, int page, int size, OrderSortInput sort) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(RECENT_ORDER_WINDOW_DAYS);
        Pageable pageable = PageRequest.of(page, size, OrderSorts.toSort(sort));

        Page<Order> orders = orderRepository.findByUserIdAndCreatedAtAfter(userId, cutoff, pageable);
        return PageDto.from(orders, toSummaryDtos(orders.getContent()));
    }

    /**
     * Customer "View Older History" — orders 30+ days old, paged
     * (image 2). 10 per page, same sort options as above.
     */
    public PageDto<OrderSummaryDto> getMyOlderOrders(UUID userId, int page, int size, OrderSortInput sort) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(RECENT_ORDER_WINDOW_DAYS);
        Pageable pageable = PageRequest.of(page, size, OrderSorts.toSort(sort));

        Page<Order> orders = orderRepository.findByUserIdAndCreatedAtBefore(userId, cutoff, pageable);
        return PageDto.from(orders, toSummaryDtos(orders.getContent()));
    }

    /**
     * Higher-role (owner/admin/manager/receptionist) orders table
     * (image 3), paged, filterable by status + free-text search,
     * sortable. 10/page.
     */
    public PageDto<OrderSummaryDto> getAllOrders(OrderFilterInput filter) {
        Pageable pageable = PageRequest.of(filter.page(), filter.size(), OrderSorts.toSort(filter.sort()));
        var spec = OrderSpecifications.withFilters(filter.search(), filter.status());

        Page<Order> orders = orderRepository.findAll(spec, pageable);
        return PageDto.from(orders, toSummaryDtos(orders.getContent()));
    }

    /**
     * Higher-role order detail view (image 4), reached via the
     * "..." -> "View details" action on a row. Not scoped to a user —
     * any of the higher roles can open any order (enforced at the
     * resolver via @PreAuthorize).
     */
    public OrderDetailDto getOrderDetail(UUID orderId) {
        Order order = findOrderOrThrow(orderId);
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        return orderMapper.toDetailDto(order, items);
    }

    /**
     * Customer viewing a single one of their own orders. Scoped to the
     * owning user — throws if the order exists but belongs to someone
     * else, so customers can't view arbitrary order IDs.
     */
    public OrderDetailDto getMyOrderDetail(UUID userId, UUID orderId) {
        Order order = findOrderOrThrow(orderId);

        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        return orderMapper.toDetailDto(order, items);
    }

    private Order findOrderOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private List<OrderSummaryDto> toSummaryDtos(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }

        List<UUID> orderIds = orders.stream().map(Order::getId).toList();

        // Single query for the whole page instead of one per order.
        Map<UUID, List<OrderItem>> itemsByOrderId = orderItemRepository.findByOrderIdIn(orderIds).stream()
            .collect(Collectors.groupingBy(item -> item.getOrder().getId()));

        return orders.stream()
            .map(order -> orderMapper.toSummaryDto(
                order,
                itemsByOrderId.getOrDefault(order.getId(), List.of())))
            .toList();
    }
}