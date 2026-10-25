package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.dtos.giftcards.GiftCardBalanceApplicationResult;
import com.verdant.salon_ecomm.dtos.order.OrderDto;
import com.verdant.salon_ecomm.dtos.order.OrderPage;
import com.verdant.salon_ecomm.dtos.order.PlaceOrderInput;
import com.verdant.salon_ecomm.dtos.order.admin.*;
import com.verdant.salon_ecomm.dtos.order.events.OrderCreatedByAdminEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderPlacedEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderUpdatedEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrdersDeletedEvent;
import com.verdant.salon_ecomm.dtos.giftcards.events.OrderPaymentVoidedEvent;
import com.verdant.salon_ecomm.dtos.payment.PaymentStatusChangedEvent;
import com.verdant.salon_ecomm.exceptions.UnpaidOrderLimitException;
import com.verdant.salon_ecomm.exceptions.InsufficientStockException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.OrderMapper;
import com.verdant.salon_ecomm.models.entities.*;
import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.orders.*;
import com.verdant.salon_ecomm.repositories.OrderItemRepository;
import com.verdant.salon_ecomm.repositories.OrderRepository;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.specifications.OrderSpec;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.verdant.salon_ecomm.utils.PageRequests;

@Service
@Validated
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
    private final GiftCardService giftCardService;
    private final PromoCodeService promoCodeService;
    private final ShippingService shippingService;
    private final ApplicationEventPublisher eventPublisher;

    // H-5: unpaid states in which an order still holds reserved stock and can still be abandoned.
    // PROCESSED is left out: an async payment (e.g. bank debit) in that state may still succeed.
    static final Set<PaymentStatus> UNPAID_RESERVING_STATUSES =
        EnumSet.of(PaymentStatus.PENDING, PaymentStatus.REQUIRES_ACTION, PaymentStatus.FAILED);

    // H-5: how many unpaid orders one customer may keep open at once. Soft limit (two parallel
    // checkouts can both pass the count); its job is to stop one account draining all stock.
    private static final int MAX_OPEN_UNPAID_ORDERS = 3;

    // ---------- Queries ----------

    public OrderPage getMyOrders(
        UUID userId, OrderClientFilter status, OrderTimeframe timeframe,
        String search, OrderClientSort sort, int page, int pageSize
    ) {
        int normalizedPage = PageRequests.page(page);
        int normalizedPageSize = PageRequests.size(pageSize, PageRequests.DEFAULT_MAX_SIZE);

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
        // Order.user is nullable; an order with no owner is visible to staff only.
        if (!isAdmin && (order.getUser() == null || !order.getUser().getId().equals(currentUserId))) {
            throw new AccessDeniedException("Not your order");
        }
        return order;
    }

    public AdminOrderPage getAdminOrders(
        OrderStatus status, String search,
        AdminOrderSort sort, OrderSortDirection direction, int page, int pageSize
    ) {
        int normalizedPage = PageRequests.page(page);
        int normalizedPageSize = PageRequests.size(pageSize, PageRequests.DEFAULT_MAX_SIZE);

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
    public AdminOrderDto createAdminOrder(@Valid AdminCreateOrderInput input, User actor) {
        if (input.fullName() == null || input.fullName().isBlank()) {
            throw new IllegalArgumentException("Customer full name is required for admin order creation");
        }
        if (input.phone() == null || input.phone().isBlank()) {
            throw new IllegalArgumentException("Customer phone number is required for admin order creation");
        }
        if (input.fullName().trim().length() > 100) {
            throw new IllegalArgumentException("Customer full name must be at most 100 characters");
        }
        if (input.phone().trim().length() > 20) {
            throw new IllegalArgumentException("Customer phone number must be at most 20 characters");
        }
        if (input.items() == null || input.items().isEmpty()) {
            throw new IllegalArgumentException("At least one item is required");
        }

        // The name/phone typed here belong to this order only; the customer's saved profile is left alone.
        User user = resolveCustomer(input.userId(), input.email());

        Address address = orderMapper.fromAddressInput(input.shippingAddress());

        ItemBuildResult built = buildOrderItems(input.items());
        List<OrderItem> pendingItems = built.items();
        BigDecimal subtotal = built.subtotal();

        BigDecimal promoDiscount = BigDecimal.ZERO;
        if (input.promoCode() != null && !input.promoCode().isBlank()) {
            promoDiscount = promoCodeService.validate(user.getId(), input.promoCode(), subtotal).discountAmount();
        }

        List<DeliveryOption> deliveryOptions = pendingItems.stream().map(OrderItem::getDeliveryOption).toList();
        Order savedOrder = buildAndSaveOrder(
            user, address, input.paymentMethod(), subtotal, promoDiscount, deliveryOptions
        );
        savedOrder.setContactName(input.fullName().trim());
        savedOrder.setContactPhone(input.phone().trim());

        pendingItems.forEach(item -> item.setOrder(savedOrder));
        List<OrderItem> savedItems = orderItemRepository.saveAll(pendingItems);

        if (promoDiscount.signum() > 0) {
            if (input.promoCode() == null) {
                throw new IllegalStateException("Promo discount applied without a promo code");
            }
            promoCodeService.recordRedemption(user.getId(), input.promoCode(), promoDiscount, savedOrder);
        }

        eventPublisher.publishEvent(new OrderCreatedByAdminEvent(savedOrder, actor));

        return orderMapper.toAdminDto(savedOrder, savedItems);
    }

    @Transactional
    public AdminOrderDto updateAdminOrder(UUID id, @Valid AdminUpdateOrderInput input, User actor) {
        Order order = orderRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        OrderStatus previousOrderStatus = order.getOrderStatus();
        PaymentStatus previousPaymentStatus = order.getPaymentStatus();

        if (input.shippingAddress() != null) {
            order.setShippingAddress(orderMapper.fromAddressInput(input.shippingAddress()));
        }
        if (input.fullName() != null && !input.fullName().isBlank()) {
            if (input.fullName().trim().length() > 100) {
                throw new IllegalArgumentException("Customer full name must be at most 100 characters");
            }
            order.setContactName(input.fullName().trim());
        }
        if (input.phone() != null && !input.phone().isBlank()) {
            if (input.phone().trim().length() > 20) {
                throw new IllegalArgumentException("Customer phone number must be at most 20 characters");
            }
            order.setContactPhone(input.phone().trim());
        }
        if (input.paymentMethod() != null) { order.setPaymentMethod(input.paymentMethod()); }
        if (input.orderStatus() != null) { order.setOrderStatus(input.orderStatus()); }
        if (input.paymentStatus() != null) { order.setPaymentStatus(input.paymentStatus()); }

        // Cancelling an order or marking its payment canceled must hand back any gift-card wallet
        // money used on it (idempotent - safe if the Stripe webhook already did it).
        // FAILED no longer counts (H-3): the customer can still retry the card and pay, and a refunded
        // wallet plus a successful retry would pay out twice. Use CANCELLED to give the money back.
        boolean orderJustCancelled = input.orderStatus() == OrderStatus.CANCELLED
            && previousOrderStatus != OrderStatus.CANCELLED;
        boolean paymentJustVoided = input.paymentStatus() != null
            && input.paymentStatus() != previousPaymentStatus
            && input.paymentStatus() == PaymentStatus.CANCELLED;
        if (orderJustCancelled || paymentJustVoided) {
            giftCardService.restoreWalletForOrder(order);
        }

        // H-5: undoing a cancel takes the stock again first (fails with INSUFFICIENT_STOCK if it is
        // gone), so the item reconciliation below works on an order that really holds its stock.
        OrderStatus targetOrderStatus = order.getOrderStatus();
        if (order.isStockReleased() && targetOrderStatus != OrderStatus.CANCELLED) {
            reReserveStock(order);
        }
        // Editing quantities of a cancelled order would move stock for goods that are not reserved.
        if (targetOrderStatus == OrderStatus.CANCELLED && input.items() != null) {
            throw new IllegalArgumentException("Items of a cancelled order can't be edited. Re-open the order first.");
        }

        List<OrderItem> items;
        if (input.items() != null) {
            List<UUID> requestedProductIds = input.items().stream()
                .map(AdminOrderItemInput::productId)
                .toList();

            Set<UUID> seen = new HashSet<>();
            Set<UUID> duplicateProductIds = requestedProductIds.stream()
                .filter(productId -> !seen.add(productId))
                .collect(Collectors.toSet());

            if (!duplicateProductIds.isEmpty()) {
                throw new ValidationException("Duplicate product IDs in order items: " + duplicateProductIds);
            }

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

            Map<UUID, MediaImageDto> primaryImages = resolvePrimaryImages(requestedProductIds);

            List<OrderItem> reconciledItems = new ArrayList<>();
            BigDecimal subtotal = BigDecimal.ZERO;
            for (AdminOrderItemInput itemInput : input.items()) {
                OrderItem existing = existingByProduct.get(itemInput.productId());

                if (existing != null) {
                    if (!existing.getQuantity().equals(itemInput.quantity())) {
                        reconcileStockForQuantityChange(
                            itemInput.productId(),
                            existing.getQuantity(),
                            itemInput.quantity()
                        );
                        existing.setQuantity(itemInput.quantity());
                        existing.setSubtotal(
                            existing.getUnitPrice().multiply(
                                BigDecimal.valueOf(itemInput.quantity())
                            )
                        );
                    }
                    existing.setDeliveryOption(itemInput.deliveryOption());
                    subtotal = subtotal.add(existing.getSubtotal());
                    reconciledItems.add(existing);
                } else {
                    Product product = productRepository.findById(itemInput.productId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found: " + itemInput.productId())
                        );
                    reserveStock(product.getId(), itemInput.quantity());
                    String productImage = resolveImageUrl(primaryImages, product.getId());
                    OrderItem newItem = orderMapper.toItemEntity(
                        order, product, productImage,
                        itemInput.quantity(),
                        itemInput.deliveryOption()
                    );
                    subtotal = subtotal.add(newItem.getSubtotal());
                    reconciledItems.add(newItem);
                }
            }

            List<OrderItem> removedItems = existingItems.stream()
                .filter(item -> !requestedByProduct
                    .containsKey(item.getProduct().getId()))
                .toList();
            if (!removedItems.isEmpty()) {
                orderItemRepository.deleteAll(removedItems);
                orderItemRepository.flush();
            }

            List<DeliveryOption> updatedDeliveryOptions = reconciledItems.stream()
                .map(OrderItem::getDeliveryOption)
                .toList();
            BigDecimal updatedDeliveryFee = shippingService.calculateDeliveryFee(updatedDeliveryOptions);

            order.setSubtotal(subtotal);
            order.setDeliveryFee(updatedDeliveryFee);
            order.setTotal(subtotal.subtract(order.getPromoDiscountAmount()).add(updatedDeliveryFee));
            items = orderItemRepository.saveAll(reconciledItems);
        }
        else { items = orderItemRepository.findByOrder_Id(id); }

        // H-5: a cancel before shipping puts the goods back on the shelf, paid or not
        // (refunding a paid order is a separate step). Shipped-then-cancelled orders keep their stock out.
        if (orderJustCancelled && isNotShipped(previousOrderStatus)) {
            releaseStock(order);
        }

        Order savedOrder = orderRepository.save(order);

        eventPublisher.publishEvent(
            new OrderUpdatedEvent(
                savedOrder, actor, previousOrderStatus, previousPaymentStatus
            )
        );

        return orderMapper.toAdminDto(savedOrder, items);
    }

    @Transactional
    public Order placeOrder(UUID userId, @Valid PlaceOrderInput input) {
        // H-5: placing orders reserves stock before payment, so cap how many unpaid ones a
        // customer can hold. Abandoned ones are expired by UnpaidOrderExpiryJob.
        long openUnpaid = orderRepository.countOpenUnpaid(userId, OrderStatus.PLACED, UNPAID_RESERVING_STATUSES);
        if (openUnpaid >= MAX_OPEN_UNPAID_ORDERS) {
            throw new UnpaidOrderLimitException(
                "You have " + openUnpaid + " unpaid orders. Complete or wait for them to expire before placing another.");
        }

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

        BigDecimal promoDiscount = BigDecimal.ZERO;
        if (input.promoCode() != null && !input.promoCode().isBlank()) {
            promoDiscount = promoCodeService.validate(userId, input.promoCode(), subtotal).discountAmount();
        }

        List<DeliveryOption> deliveryOptions = pendingItems.stream().map(OrderItem::getDeliveryOption).toList();
        Order savedOrder = buildAndSaveOrder(
            user, address, input.paymentMethod(), subtotal, promoDiscount, deliveryOptions
        );

        Order finalSavedOrder = savedOrder;
        pendingItems.forEach(item -> item.setOrder(finalSavedOrder));
        orderItemRepository.saveAll(pendingItems);

        if (promoDiscount.signum() > 0) {
            if (input.promoCode() == null) {
                throw new IllegalStateException("Promo discount applied without a promo code");
            }
            promoCodeService.recordRedemption(userId, input.promoCode(), promoDiscount, savedOrder);
        }

        if (Boolean.TRUE.equals(input.useWalletBalance())) {
            GiftCardBalanceApplicationResult result =
                giftCardService.applyBalanceToOrder(userId, savedOrder.getTotal(), savedOrder);
            if (result.totalApplied().signum() > 0) {
                savedOrder.setWalletAmountApplied(result.totalApplied());
                if (result.totalApplied().compareTo(savedOrder.getTotal()) >= 0) {
                    savedOrder.setPaymentStatus(PaymentStatus.PAID);
                }
                savedOrder = orderRepository.save(savedOrder);
            }
        }

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

        // Must run before detachOrderReferences: the wallet refund rows it writes point at the order
        // and need unlinking too.
        orders.stream()
            .filter(this::holdsUnpaidReservations)
            .forEach(this::releaseUnpaidReservations);

        // Keep the gift-card ledger rows but unlink them, otherwise gift_card_transactions.order_id
        // blocks delete with an FK violation for any order paid (even partly) from the wallet.
        giftCardService.detachOrderReferences(foundIds);

        orders.forEach(order -> orderItemRepository
            .deleteAll(orderItemRepository.findByOrder_Id(order.getId())));
        orderRepository.deleteAll(orders);

        // Published before the transaction commits but after deleteAll is queued —
        // listeners run AFTER_COMMIT (see OrderAuditListener/OrderNotificationListener),
        // so by the time they read `orders`, rows are gone from the DB but this
        // in-memory list still holds the data they need to describe what was deleted.
        eventPublisher.publishEvent(new OrdersDeletedEvent(orders, actor));

        return orders;
    }

    // H-5: Stripe canceled the PaymentIntent (the expiry job did it, or someone in the dashboard).
    // That intent can never be paid, so an order that hasn't shipped is cancelled and its stock returned.
    // Plain @EventListener: runs inside the webhook's transaction, next to GiftCardService's wallet refund.
    @EventListener
    @Transactional
    public void onOrderPaymentVoided(OrderPaymentVoidedEvent event) {
        Order order = event.order();
        OrderStatus status = order.getOrderStatus();
        // An already CANCELLED order was handled by the admin path; shipped orders keep their stock out.
        if (status != OrderStatus.PLACED && status != OrderStatus.PROCESSING) return;

        order.setOrderStatus(OrderStatus.CANCELLED);
        releaseStock(order);
        orderRepository.save(order);
    }

    // H-5: oldest unpaid orders past the cutoff, for UnpaidOrderExpiryJob.
    public List<Order> findExpiredUnpaidOrders(OffsetDateTime cutoff, int limit) {
        return orderRepository.findExpiredUnpaid(
            OrderStatus.PLACED, UNPAID_RESERVING_STATUSES, cutoff, PageRequest.of(0, limit));
    }

    // H-5: called by UnpaidOrderExpiryJob only after the order's Stripe intent (if any) was
    // canceled, so the customer can no longer pay for goods that are being put back.
    // Re-checks under a row lock: a payment or an admin may have changed the order since it was listed.
    @Transactional
    public boolean expireUnpaidOrder(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null
            || order.getOrderStatus() != OrderStatus.PLACED
            || !UNPAID_RESERVING_STATUSES.contains(order.getPaymentStatus())) {
            return false;
        }

        PaymentStatus previousPayment = order.getPaymentStatus();
        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.CANCELLED);
        giftCardService.restoreWalletForOrder(order); // idempotent
        releaseStock(order);
        orderRepository.save(order);

        // OrderUpdatedEvent needs a staff actor, so the system-side change goes out as a payment
        // status change (same event the Stripe webhooks publish; owner notified and audited).
        UUID ownerId = order.getUser() != null ? order.getUser().getId() : null;
        eventPublisher.publishEvent(new PaymentStatusChangedEvent(
            order.getId(), ownerId, previousPayment, PaymentStatus.CANCELLED, "order.unpaid_expired"));
        return true;
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

    private Order buildAndSaveOrder(
        User user, Address address, String paymentMethod,
        BigDecimal subtotal, BigDecimal promoDiscount,
        List<DeliveryOption> deliveryOptions
    ) {
        // One fee per order, set by the most expensive delivery option among the items (see ShippingService).
        BigDecimal deliveryFee = shippingService.calculateDeliveryFee(deliveryOptions);
        BigDecimal total = subtotal.subtract(promoDiscount).add(deliveryFee);

        Order order = orderMapper.toEntity(
            user, address, paymentMethod,
            subtotal, deliveryFee, total
        );
        order.setPromoDiscountAmount(promoDiscount);
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
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Product not found: " + itemInput.productId())
                );

            reserveStock(product.getId(), itemInput.quantity());

            String productImage = resolveImageUrl(primaryImages, product.getId());
            OrderItem item = orderMapper.toItemEntity(
                null, product, productImage,
                itemInput.quantity(),
                itemInput.deliveryOption()
            );
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

    // An order that was never paid and never left the shop still holds the stock reserved at checkout
    // and any wallet money drawn for it. PROCESSED is excluded because that payment may still succeed.
    // Cancel/expiry may already have returned the stock: releaseStock() skips those via the flag.
    private boolean holdsUnpaidReservations(Order order) {
        PaymentStatus payment = order.getPaymentStatus();
        boolean unpaid = payment == null
            || payment == PaymentStatus.PENDING
            || payment == PaymentStatus.REQUIRES_ACTION
            || payment == PaymentStatus.FAILED
            || payment == PaymentStatus.CANCELLED;
        OrderStatus status = order.getOrderStatus();
        boolean notShipped = status == null
            || status == OrderStatus.PLACED
            || status == OrderStatus.PROCESSING
            || status == OrderStatus.CANCELLED;
        return unpaid && notShipped;
    }

    private void releaseUnpaidReservations(Order order) {
        giftCardService.restoreWalletForOrder(order); // idempotent
        releaseStock(order);
    }

    // H-5: the single place stock goes back for a dead order. claimStockRelease is an atomic
    // UPDATE ... WHERE stock_released = false, so a second caller (webhook, job, admin, delete)
    // gets 0 and does nothing. The entity flag is set too, or saving this managed order later
    // would write the stale false back over the claim.
    private void releaseStock(Order order) {
        if (orderRepository.claimStockRelease(order.getId()) == 0) return;
        order.setStockReleased(true);
        for (OrderItem item : orderItemRepository.findByOrder_Id(order.getId())) {
            if (item.getProduct() != null && item.getQuantity() != null) {
                restoreStock(item.getProduct().getId(), item.getQuantity());
            }
        }
    }

    // Undo of releaseStock when an admin re-opens a cancelled order. reserveStock throws
    // InsufficientStockException (rolling the whole edit back) if the goods were sold meanwhile.
    private void reReserveStock(Order order) {
        for (OrderItem item : orderItemRepository.findByOrder_Id(order.getId())) {
            if (item.getProduct() != null && item.getQuantity() != null) {
                reserveStock(item.getProduct().getId(), item.getQuantity());
            }
        }
        orderRepository.unclaimStockRelease(order.getId());
        order.setStockReleased(false);
    }

    private boolean isNotShipped(OrderStatus status) {
        return status == null || status == OrderStatus.PLACED || status == OrderStatus.PROCESSING;
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
        Sort.Direction effectiveDirection =
            direction == OrderSortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;

        String field = switch (effectiveSort) {
            case DATE -> "createdAt";
            case TOTAL -> "total";
            case STATUS -> "orderStatus";
        };

        return Sort.by(effectiveDirection, field);
    }
}