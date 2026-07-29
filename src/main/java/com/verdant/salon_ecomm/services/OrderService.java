package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.dtos.order.*;
import com.verdant.salon_ecomm.dtos.order.admin.*;
import com.verdant.salon_ecomm.entities.*;
import com.verdant.salon_ecomm.dtos.order.events.OrderCreatedByAdminEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderPlacedEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderUpdatedEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrdersDeletedEvent;
import com.verdant.salon_ecomm.exceptions.InsufficientStockException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.OrderMapper;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import com.verdant.salon_ecomm.models.enums.orders.*;
import com.verdant.salon_ecomm.repositories.*;
import com.verdant.salon_ecomm.specifications.OrderSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderMapper orderMapper;

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final MediaImageService mediaImageService;
    private final CartService cartService;
    private final ApplicationEventPublisher eventPublisher;

    // ---------- Queries ----------

    public OrderPage getMyOrders(
        UUID userId, OrderClientFilter status, OrderTimeframe timeframe,
        String search, OrderClientSort sort, int page, int pageSize
    ) {
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.clamp(pageSize, 1, 100);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime windowStart = now.minusDays(30);
        boolean archived = timeframe == OrderTimeframe.ARCHIVED;

        Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedPageSize, toClientSort(sort));

        Specification<Order> spec = OrderSpec.filterMyOrders(
            userId, status, search, windowStart, now, archived
        );

        Page<Order> result = orderRepository.findAll(spec, pageable);

        Map<UUID, List<OrderItem>> itemsByOrder = fetchItemsByOrder(result.getContent());

        List<OrderDto> items = result.getContent().stream()
            .map(order -> orderMapper.toDto(order, itemsByOrder.getOrDefault(order.getId(), List.of())))
            .toList();

        return new OrderPage(
            items,
            normalizedPage,
            normalizedPageSize,
            (int) result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public Order getOrderById(UUID id, UUID currentUserId, boolean isAdmin) {
        Order order = findOrderOrThrow(id);
        if (!isAdmin && !order.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Not your order");
        }
        return order;
    }

    public AdminOrderPage getAdminOrders(
        OrderStatus status, String search,
        AdminOrderSort sort, OrderSortDirection direction, int page, int pageSize
    ) {
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.clamp(pageSize, 1, 100);

        Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedPageSize, toAdminSort(sort, direction));

        Specification<Order> spec = OrderSpec.filterAdminOrders(status, search);
        Page<Order> result = orderRepository.findAll(spec, pageable);

        Map<UUID, List<OrderItem>> itemsByOrder = fetchItemsByOrder(result.getContent());

        List<AdminOrderDto> items = result.getContent().stream()
            .map(order -> orderMapper.toAdminDto(
                order, itemsByOrder.getOrDefault(order.getId(), List.of())))
            .toList();

        return new AdminOrderPage(
            items,
            normalizedPage,
            normalizedPageSize,
            (int) result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public AdminOrderDto getAdminOrderById(UUID id) {
        Order order = findOrderOrThrow(id);
        return orderMapper.toAdminDto(order, orderItemRepository.findByOrder_Id(id));
    }

    // ---------- Mutations ----------

    @Transactional
    public AdminOrderDto createAdminOrder(AdminCreateOrderInput input, User actor) {
        User user = resolveCustomer(input.userId(), input.email());

        if (input.items() == null || input.items().isEmpty()) {
            throw new IllegalArgumentException("At least one item is required");
        }

        Address address = orderMapper.fromAddressInput(input.shippingAddress());

        ItemBuildResult built = buildOrderItems(input.items());
        List<OrderItem> pendingItems = built.items();
        BigDecimal subtotal = built.subtotal();

        Order savedOrder = buildAndSaveOrder(user, address, input.paymentMethod(), subtotal);

        pendingItems.forEach(item -> item.setOrder(savedOrder));
        List<OrderItem> savedItems = orderItemRepository.saveAll(pendingItems);

        eventPublisher.publishEvent(new OrderCreatedByAdminEvent(savedOrder, actor));

        return orderMapper.toAdminDto(savedOrder, savedItems);
    }

    @Transactional
    public AdminOrderDto updateAdminOrder(UUID id, AdminUpdateOrderInput input, User actor) {
        Order order = findOrderOrThrow(id);

        OrderStatus previousOrderStatus = order.getOrderStatus();
        PaymentStatus previousPaymentStatus = order.getPaymentStatus();

        if (input.shippingAddress() != null) {
            order.setShippingAddress(orderMapper.fromAddressInput(input.shippingAddress()));
        }
        if (input.paymentMethod() != null) { order.setPaymentMethod(input.paymentMethod()); }
        if (input.orderStatus() != null) { order.setOrderStatus(input.orderStatus()); }
        if (input.paymentStatus() != null) { order.setPaymentStatus(input.paymentStatus()); }

        List<OrderItem> items;
        if (input.items() != null) {
            List<OrderItem> existingItems = orderItemRepository.findByOrder_Id(id);
            Map<UUID, OrderItem> existingByProduct = existingItems.stream()
                .collect(Collectors.toMap(item -> item.getProduct().getId(), item -> item));
            Map<UUID, AdminOrderItemInput> requestedByProduct = input.items().stream()
                .collect(Collectors.toMap(AdminOrderItemInput::productId, i -> i));

            for (OrderItem existing : existingItems) {
                UUID productId = existing.getProduct().getId();
                if (!requestedByProduct.containsKey(productId)) {
                    restoreStock(productId, existing.getQuantity());
                }
            }

            List<UUID> productIds = input.items().stream()
                .map(AdminOrderItemInput::productId)
                .toList();
            Map<UUID, MediaImageDto> primaryImages = resolvePrimaryImages(productIds);

            List<OrderItem> reconciledItems = new ArrayList<>();
            BigDecimal subtotal = BigDecimal.ZERO;
            for (AdminOrderItemInput itemInput : input.items()) {
                OrderItem existing = existingByProduct.get(itemInput.productId());

                if (existing != null) {
                    if (!existing.getQuantity().equals(itemInput.quantity())) {
                        reconcileStockForQuantityChange(itemInput.productId(), existing.getQuantity(), itemInput.quantity());
                        existing.setQuantity(itemInput.quantity());
                        existing.setSubtotal(existing.getUnitPrice().multiply(BigDecimal.valueOf(itemInput.quantity())));
                    }
                    existing.setDeliveryOption(itemInput.deliveryOption());
                    subtotal = subtotal.add(existing.getSubtotal());
                    reconciledItems.add(existing);
                } else {
                    Product product = productRepository.findById(itemInput.productId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemInput.productId()));
                    reserveStock(product.getId(), itemInput.quantity());
                    String productImage = resolveImageUrl(primaryImages, product.getId());
                    OrderItem newItem = orderMapper.toItemEntity(order, product, productImage, itemInput.quantity(), itemInput.deliveryOption());
                    subtotal = subtotal.add(newItem.getSubtotal());
                    reconciledItems.add(newItem);
                }
            }

            List<OrderItem> removedItems = existingItems.stream()
                .filter(item -> !requestedByProduct.containsKey(item.getProduct().getId()))
                .toList();
            if (!removedItems.isEmpty()) {
                orderItemRepository.deleteAll(removedItems);
                orderItemRepository.flush();
            }

            order.setSubtotal(subtotal);
            order.setTotal(subtotal.add(order.getDeliveryFee()));
            items = orderItemRepository.saveAll(reconciledItems);
        }
        else { items = orderItemRepository.findByOrder_Id(id); }

        Order savedOrder = orderRepository.save(order);

        eventPublisher.publishEvent(
            new OrderUpdatedEvent(savedOrder, actor, previousOrderStatus, previousPaymentStatus)
        );

        return orderMapper.toAdminDto(savedOrder, items);
    }

    @Transactional
    public Order placeOrder(UUID userId, PlaceOrderInput input) {
        List<CartItem> cartItems = cartService.getOwnedItems(userId, input.cartItemIds());

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Address address = orderMapper.fromAddressInput(input.shippingAddress());

        List<UUID> productIds = cartItems.stream()
            .map(item -> item.getProduct().getId())
            .toList();
        Map<UUID, MediaImageDto> primaryImages = resolvePrimaryImages(productIds);

        List<OrderItem> pendingItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : cartItems) {
            OrderItem item = reserveStockAndBuildItem(cartItem, primaryImages);
            subtotal = subtotal.add(item.getSubtotal());
            pendingItems.add(item);
        }

        Order savedOrder = buildAndSaveOrder(user, address, input.paymentMethod(), subtotal);

        pendingItems.forEach(item -> item.setOrder(savedOrder));
        orderItemRepository.saveAll(pendingItems);

        cartService.removeItems(userId, input.cartItemIds());

        eventPublisher.publishEvent(new OrderPlacedEvent(savedOrder, user));

        return savedOrder;
    }

    @Transactional
    public List<Order> adminDeleteOrders(List<UUID> orderIds, User actor) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new IllegalArgumentException("No order ids were provided.");
        }

        List<Order> orders = orderRepository.findAllById(orderIds);

        Set<UUID> foundIds = orders.stream().map(Order::getId).collect(Collectors.toSet());
        Set<UUID> requestedIds = new HashSet<>(orderIds);

        if (!foundIds.equals(requestedIds)) {
            throw new ResourceNotFoundException("One or more orders were not found.");
        }

        orders.forEach(order -> orderItemRepository.deleteAll(orderItemRepository.findByOrder_Id(order.getId())));
        orderRepository.deleteAll(orders);

        // Published before the transaction commits but after deleteAll is queued —
        // listeners run AFTER_COMMIT (see OrderAuditListener/OrderNotificationListener),
        // so by the time they read `orders`, rows are gone from the DB but this
        // in-memory list still holds the data they need to describe what was deleted.
        eventPublisher.publishEvent(new OrdersDeletedEvent(orders, actor));

        return orders;
    }

    // ---------- Helpers ----------

    private Map<UUID, List<OrderItem>> fetchItemsByOrder(List<Order> orders) {
        List<UUID> orderIds = orders.stream().map(Order::getId).toList();
        return orderItemRepository.findByOrder_IdIn(orderIds).stream()
            .collect(Collectors.groupingBy(item -> item.getOrder().getId()));
    }

    private Map<UUID, MediaImageDto> resolvePrimaryImages(List<UUID> productIds) {
        return mediaImageService.getPrimaryImagesByEntityIds(ItemType.PRODUCT, productIds);
    }

    private String resolveImageUrl(Map<UUID, MediaImageDto> primaryImages, UUID productId) {
        MediaImageDto primaryImage = primaryImages.get(productId);
        return primaryImage != null ? primaryImage.url() : null;
    }

    private Order buildAndSaveOrder(User user, Address address, String paymentMethod, BigDecimal subtotal) {
        // Shipping is currently free across the board (matches cart screen showing 0 per item) —
        // revisit if per-delivery-option shipping costs get introduced later.
        BigDecimal deliveryFee = BigDecimal.ZERO;
        BigDecimal total = subtotal.add(deliveryFee);

        Order order = orderMapper.toEntity(user, address, paymentMethod, subtotal, deliveryFee, total);
        order.setOrderCode(generateOrderCode());

        return orderRepository.save(order);
    }

    private ItemBuildResult buildOrderItems(List<AdminOrderItemInput> itemInputs) {
        List<UUID> productIds = itemInputs.stream()
            .map(AdminOrderItemInput::productId)
            .toList();
        Map<UUID, MediaImageDto> primaryImages = resolvePrimaryImages(productIds);

        List<OrderItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (AdminOrderItemInput itemInput : itemInputs) {
            Product product = productRepository.findById(itemInput.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemInput.productId()));

            reserveStock(product.getId(), itemInput.quantity());

            String productImage = resolveImageUrl(primaryImages, product.getId());
            OrderItem item = orderMapper.toItemEntity(null, product, productImage, itemInput.quantity(), itemInput.deliveryOption());
            subtotal = subtotal.add(item.getSubtotal());
            items.add(item);
        }

        return new ItemBuildResult(items, subtotal);
    }

    private record ItemBuildResult(List<OrderItem> items, BigDecimal subtotal) {}

    private Order findOrderOrThrow(UUID id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    private User resolveCustomer(UUID userId, String email) {
        if (userId != null) {
            return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        }
        if (email != null && !email.isBlank()) {
            return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email: " + email));
        }
        throw new IllegalArgumentException("userId or email is required to identify an existing customer");
    }

    private void reserveStock(UUID productId, int quantity) {
        int updatedRows = productRepository.decrementStock(productId, quantity);
        if (updatedRows == 0) {
            throw new InsufficientStockException(
                "Not enough stock for product " + productId + " (requested " + quantity + ")"
            );
        }
    }

    private void restoreStock(UUID productId, int quantity) {
        if (quantity > 0) {
            productRepository.incrementStock(productId, quantity);
        }
    }

    private void reconcileStockForQuantityChange(UUID productId, int oldQuantity, int newQuantity) {
        int delta = newQuantity - oldQuantity;
        if (delta > 0) {
            reserveStock(productId, delta);
        } else if (delta < 0) {
            restoreStock(productId, -delta);
        }
    }

    private OrderItem reserveStockAndBuildItem(CartItem cartItem, Map<UUID, MediaImageDto> primaryImages) {
        Product product = cartItem.getProduct();
        int quantity = cartItem.getQuantity();

        reserveStock(product.getId(), quantity);

        String productImage = resolveImageUrl(primaryImages, product.getId());
        return orderMapper.toItemEntity(null, product, productImage, quantity, cartItem.getDeliveryOption());
    }

    private String generateOrderCode() {
        Long sequenceValue = orderRepository.getNextOrderCodeSequenceValue();
        return "VS-" + String.format("%08d", sequenceValue);
    }

    private Sort toClientSort(OrderClientSort sort) {
        OrderClientSort effective = sort != null ? sort : OrderClientSort.NEWEST;
        return switch (effective) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case HIGHEST_TOTAL -> Sort.by(Sort.Direction.DESC, "total");
        };
    }

    private Sort toAdminSort(AdminOrderSort sort, OrderSortDirection direction) {
        AdminOrderSort effectiveSort = sort != null ? sort : AdminOrderSort.DATE;
        Sort.Direction effectiveDirection = direction == OrderSortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;

        String field = switch (effectiveSort) {
            case DATE -> "createdAt";
            case TOTAL -> "total";
            case STATUS -> "orderStatus";
        };

        return Sort.by(effectiveDirection, field);
    }
}