package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.dtos.giftcards.GiftCardBalanceApplicationResult;
import com.verdant.salon_ecomm.dtos.giftcards.events.OrderPaymentVoidedEvent;
import com.verdant.salon_ecomm.dtos.order.OrderPage;
import com.verdant.salon_ecomm.dtos.order.PlaceOrderInput;
import com.verdant.salon_ecomm.dtos.order.admin.*;
import com.verdant.salon_ecomm.dtos.order.events.OrderCancelledByCustomerEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderCreatedByAdminEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderPlacedEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderUpdatedEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrdersDeletedEvent;
import com.verdant.salon_ecomm.dtos.payment.PaymentStatusChangedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductStockChangedEvent;
import com.verdant.salon_ecomm.exceptions.InsufficientStockException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.config.StripeConfig;
import com.verdant.salon_ecomm.exceptions.CheckoutFailedException;
import com.verdant.salon_ecomm.mappers.OrderMapper;
import com.verdant.salon_ecomm.models.entities.*;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.orders.*;
import com.verdant.salon_ecomm.repositories.OrderItemRepository;
import com.verdant.salon_ecomm.repositories.OrderRepository;
import com.verdant.salon_ecomm.repositories.PendingCheckoutRepository;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.specifications.OrderSpec;
import com.verdant.salon_ecomm.utils.PageRequests;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final PendingCheckoutRepository pendingCheckoutRepository;
    private final StripeConfig stripeConfig;
    private final ApplicationEventPublisher eventPublisher;

    // How long a draft checkout may wait for its payment. Long enough for 3-D Secure and a slow customer;
    // past it, UnpaidOrderExpiryJob cancels the Stripe intent and deletes the draft.
    @Value("${app.orders.draft-ttl-minutes:30}")
    private long draftTtlMinutes;

    // Unpaid states of ORDERS created before drafts existed (legacy PENDING rows). New orders are only written
    // once paid, so nothing new enters these states, but old rows still hold stock until the expiry job frees them.
    // PROCESSED is left out: an async payment (e.g. bank debit) in that state may still succeed.
    static final Set<PaymentStatus> UNPAID_RESERVING_STATUSES =
        EnumSet.of(PaymentStatus.PENDING, PaymentStatus.REQUIRES_ACTION, PaymentStatus.FAILED);

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

        // H-12: entities go straight out. Items are loaded by OrderResolver's @BatchMapping, once for
        // the whole page and only if the client asked for them, so they aren't pre-fetched here.
        return new OrderPage(
            result.getContent(),
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
    // Staff order paid in cash: no card step, the order is created as PAID right away. Card and bank go
    // through createAdminDraft (the order is created only after the payment succeeds).
    public AdminOrderDto createAdminOrder(@Valid AdminCreateOrderInput input, User actor) {
        validateAdminCreateInput(input);
        if (!ADMIN_METHOD_CASH.equals(requireAdminPaymentMethod(input.paymentMethod()))) {
            throw BusinessRuleException.invalid("Card and bank orders are created after the payment succeeds.");
        }

        // The name/phone typed here belong to this order only; the customer's saved profile is left alone.
        User user = resolveCustomer(input.userId(), input.email());

        Address address = orderMapper.fromAddressInput(input.shippingAddress());

        ItemBuildResult built = buildOrderItems(input.items());
        List<OrderItem> pendingItems = built.items();
        BigDecimal subtotal = built.subtotal();

        BigDecimal promoDiscount = BigDecimal.ZERO;
        if (input.promoCode() != null && !input.promoCode().isBlank()) {
            // M-11: locks the promo row until this order commits (per-customer limit can't be raced).
            promoDiscount = promoCodeService.validateForOrder(user.getId(), input.promoCode(), subtotal).discountAmount();
        }

        List<DeliveryOption> deliveryOptions = pendingItems.stream().map(OrderItem::getDeliveryOption).toList();
        Order savedOrder = buildAndSaveOrder(
            user, address, ADMIN_METHOD_CASH, subtotal, promoDiscount, deliveryOptions
        );
        savedOrder.setContactName(input.fullName().trim());
        savedOrder.setContactPhone(input.phone().trim());
        savedOrder.setContactEmail(resolveAdminContactEmail(input, user));
        // Cash is handed over at the till, so there is nothing to wait for.
        savedOrder.setPaymentStatus(PaymentStatus.PAID);
        savedOrder.setPaidAt(OffsetDateTime.now());

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
                throw BusinessRuleException.invalid("Customer full name must be at most 100 characters");
            }
            order.setContactName(input.fullName().trim());
        }
        if (input.phone() != null && !input.phone().isBlank()) {
            if (input.phone().trim().length() > 20) {
                throw BusinessRuleException.invalid("Customer phone number must be at most 20 characters");
            }
            order.setContactPhone(input.phone().trim());
        }
        if (input.email() != null && !input.email().isBlank()) {
            requireValidEmail(input.email().trim());
            order.setContactEmail(input.email().trim());
        }
        if (input.paymentMethod() != null) { order.setPaymentMethod(input.paymentMethod()); }
        if (input.orderStatus() != null) { order.setOrderStatus(input.orderStatus()); }
        if (input.paymentStatus() != null) { order.setPaymentStatus(input.paymentStatus()); }
        // The delivery estimate counts from payment, so note when staff mark an order paid by hand.
        if (order.getPaymentStatus() == PaymentStatus.PAID && order.getPaidAt() == null) {
            order.setPaidAt(OffsetDateTime.now());
        }

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
        if (orderJustCancelled) {
            markCancelled(order, OrderCancelledBy.STAFF, null);
        } else if (previousOrderStatus == OrderStatus.CANCELLED && order.getOrderStatus() != OrderStatus.CANCELLED) {
            clearCancellation(order); // staff re-opened it
        }

        // H-5: undoing a cancel takes the stock again first (fails with INSUFFICIENT_STOCK if it is
        // gone), so the item reconciliation below works on an order that really holds its stock.
        OrderStatus targetOrderStatus = order.getOrderStatus();
        if (order.isStockReleased() && targetOrderStatus != OrderStatus.CANCELLED) {
            reReserveStock(order);
        }
        // Editing quantities of a cancelled order would move stock for goods that are not reserved.
        if (targetOrderStatus == OrderStatus.CANCELLED && input.items() != null) {
            throw BusinessRuleException.invalid("Items of a cancelled order can't be edited. Re-open the order first.");
        }
        if (input.items() != null) {
            requireItemsEditable(order, previousPaymentStatus);
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
                // M-9: was jakarta ValidationException, which no handler mapped (500 "Something went wrong").
                throw BusinessRuleException.invalid("Each product can appear only once in an order. Merge the duplicate lines.");
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
                    requireOrderable(product); // M-19: new lines only; existing lines keep their product
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

            // M-16: the promo discount was fixed when the order was placed. Removing items could leave it
            // larger than the new subtotal and the total negative, so it is capped at the subtotal (and the
            // stored amount lowered to match, so subtotal - discount + delivery still adds up).
            // The promo itself is not re-validated (e.g. its minimum order amount): its redemption was
            // recorded at checkout, and staff are allowed to adjust the order on the customer's behalf.
            BigDecimal discount = order.getPromoDiscountAmount() != null ? order.getPromoDiscountAmount() : BigDecimal.ZERO;
            BigDecimal cappedDiscount = discount.min(subtotal);
            BigDecimal updatedTotal = subtotal.subtract(cappedDiscount).add(updatedDeliveryFee);

            // M-16: gift card money already drawn for this order can't exceed what the order now costs;
            // the surplus would be lost from the customer's wallet. Staff cancel and re-create instead.
            BigDecimal walletApplied = order.getWalletAmountApplied() != null ? order.getWalletAmountApplied() : BigDecimal.ZERO;
            if (walletApplied.compareTo(updatedTotal) > 0) {
                throw BusinessRuleException.invalid("The gift card balance already applied to this order ("
                    + walletApplied + ") is more than the new total (" + updatedTotal
                    + "). Cancel the order and create a new one instead.");
            }

            order.setSubtotal(subtotal);
            order.setDeliveryFee(updatedDeliveryFee);
            order.setPromoDiscountAmount(cappedDiscount);
            order.setTotal(updatedTotal);
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

    // ---------- Checkout drafts: an order row is written only once it is paid ----------

    private record LineRequest(UUID productId, int quantity, DeliveryOption deliveryOption) {}

    // Customer checkout (card, or fully covered by the gift-card wallet). Prices the cart, validates the promo
    // and stores all of it as a draft. Stock is only checked here, not held: it is taken when the order is
    // created (createOrderFromDraft), and if it ran out by then the payment is refunded.
    @Transactional
    public PendingCheckout createCustomerDraft(UUID userId, @Valid PlaceOrderInput input) {
        List<CartItem> cartItems = cartService.getOwnedItems(userId, input.cartItemIds());
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        List<LineRequest> lines = cartItems.stream()
            .map(ci -> new LineRequest(ci.getProduct().getId(), ci.getQuantity(), ci.getDeliveryOption()))
            .toList();

        return buildDraft(
            user, null, lines, orderMapper.fromAddressInput(input.shippingAddress()),
            input.fullName().trim(), input.phone().trim(), input.email().trim(),
            "card", input.promoCode(), Boolean.TRUE.equals(input.useWalletBalance())
        );
    }

    // Staff paying by card or bank for a customer. Same draft; the staff member types the customer's card.
    @Transactional
    public PendingCheckout createAdminDraft(@Valid AdminCreateOrderInput input, User actor) {
        validateAdminCreateInput(input);
        String method = requireAdminPaymentMethod(input.paymentMethod());
        if (method.equals(ADMIN_METHOD_CASH)) {
            throw BusinessRuleException.invalid("Cash orders need no card payment. Place the order directly.");
        }
        User customer = resolveCustomer(input.userId(), input.email());
        List<LineRequest> lines = input.items().stream()
            .map(i -> new LineRequest(i.productId(), i.quantity(), i.deliveryOption()))
            .toList();

        return buildDraft(
            customer, actor.getId(), lines, orderMapper.fromAddressInput(input.shippingAddress()),
            input.fullName().trim(), input.phone().trim(), resolveAdminContactEmail(input, customer),
            method, input.promoCode(), false
        );
    }

    private PendingCheckout buildDraft(
        User customer, UUID createdByUserId, List<LineRequest> requests, Address address,
        String name, String phone, String email, String paymentMethod, String promoCode, boolean useWallet
    ) {
        if (requests.isEmpty()) {
            throw BusinessRuleException.invalid("At least one item is required");
        }

        Map<UUID, Product> products = new HashMap<>();
        Map<UUID, Integer> wantedByProduct = new HashMap<>();
        for (LineRequest request : requests) {
            Product product = products.computeIfAbsent(request.productId(), id ->
                productRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id)));
            requireOrderable(product); // M-19
            wantedByProduct.merge(request.productId(), request.quantity(), Integer::sum);
        }
        // A check only, not a hold: it fails early for something that is already sold out.
        wantedByProduct.forEach((productId, wanted) -> {
            int inStock = productRepository.findStockLevel(productId)
                .map(level -> level.stockQuantity() != null ? level.stockQuantity() : 0)
                .orElse(0);
            if (inStock < wanted) {
                throw new InsufficientStockException(
                    "Not enough stock for product " + productId + " (requested " + wanted + ")");
            }
        });

        List<PendingCheckout.Line> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (LineRequest request : requests) {
            BigDecimal unitPrice = products.get(request.productId()).effectivePrice(); // H-4, frozen on the draft
            lines.add(new PendingCheckout.Line(
                request.productId(), request.quantity(), request.deliveryOption(), unitPrice));
            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(request.quantity())));
        }

        String code = promoCode != null && !promoCode.isBlank() ? promoCode.trim() : null;
        BigDecimal promoDiscount = BigDecimal.ZERO;
        if (code != null) {
            // M-11: locks the promo row until this transaction ends. The redemption itself is recorded when the
            // order is created, so an abandoned checkout never uses up the customer's allowance.
            promoDiscount = promoCodeService.validateForOrder(customer.getId(), code, subtotal).discountAmount();
        }

        BigDecimal deliveryFee = shippingService.calculateDeliveryFee(
            requests.stream().map(LineRequest::deliveryOption).toList());
        BigDecimal total = subtotal.subtract(promoDiscount).add(deliveryFee);

        // The wallet is drawn when the order is created, not now; this only fixes how much Stripe is asked for.
        BigDecimal wallet = BigDecimal.ZERO;
        if (useWallet) {
            wallet = giftCardService.getMyWalletBalance(customer.getId()).min(total).max(BigDecimal.ZERO);
        }

        return pendingCheckoutRepository.save(PendingCheckout.builder()
            .userId(customer.getId())
            .createdByUserId(createdByUserId)
            .items(lines)
            .shippingAddress(address)
            .contactName(name)
            .contactPhone(phone)
            .contactEmail(email)
            .paymentMethod(paymentMethod)
            .promoCode(code)
            .promoDiscountAmount(promoDiscount)
            .subtotal(subtotal)
            .deliveryFee(deliveryFee)
            .total(total)
            .walletAmountApplied(wallet)
            .amountDue(total.subtract(wallet))
            .currency(stripeConfig.getCurrency())
            .expiresAt(OffsetDateTime.now().plusMinutes(draftTtlMinutes))
            .build());
    }

    // The one place a paid checkout becomes an order. Runs in its own transaction (REQUIRES_NEW) so a failure
    // rolls back only this attempt, even when called from inside the Stripe webhook's transaction.
    //
    // Idempotent: finalizeCheckout (the browser) and the payment_intent.succeeded webhook can both call it. The
    // draft row is locked, so the second caller waits, then finds the draft gone and returns the order that exists.
    //
    // paymentIntentId is null for an order the gift-card wallet covered in full (nothing was charged).
    // Throws CheckoutFailedException (everything rolled back, draft kept) when the money can't be turned into
    // an order; the caller refunds it.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order createOrderFromDraft(UUID draftId, String paymentIntentId) {
        PendingCheckout draft = pendingCheckoutRepository.findByIdForUpdate(draftId).orElse(null);
        if (draft == null) {
            if (paymentIntentId != null) {
                Optional<Order> existing = orderRepository.findByStripePaymentIntentId(paymentIntentId);
                if (existing.isPresent()) return existing.get();
            }
            throw new ResourceNotFoundException("This checkout was not found or has expired.");
        }

        User customer = userRepository.findById(draft.getUserId())
            .orElseThrow(() -> new CheckoutFailedException("The customer account no longer exists."));
        User actor = draft.getCreatedByUserId() != null
            ? userRepository.findById(draft.getCreatedByUserId()).orElse(null) : null;

        Map<UUID, Product> products = productRepository.findAllById(
                draft.getItems().stream().map(PendingCheckout.Line::productId).distinct().toList())
            .stream().collect(Collectors.toMap(Product::getId, p -> p));
        Map<UUID, MediaImageDto> primaryImages = resolvePrimaryImages(new ArrayList<>(products.keySet()));

        // Stock is taken now, atomically per line. A sold-out line aborts (and rolls back) the whole order.
        for (PendingCheckout.Line line : draft.getItems()) {
            Product product = products.get(line.productId());
            if (product == null) {
                throw new CheckoutFailedException("An item in your order is no longer available.");
            }
            try {
                reserveStock(line.productId(), line.quantity());
            } catch (InsufficientStockException ex) {
                throw new CheckoutFailedException("\"" + product.getName() + "\" sold out while you were paying.");
            }
        }

        Order order = orderMapper.toEntity(
            customer, draft.getShippingAddress(), draft.getPaymentMethod(),
            draft.getSubtotal(), draft.getDeliveryFee(), draft.getTotal());
        order.setPromoDiscountAmount(draft.getPromoDiscountAmount());
        order.setOrderCode(generateOrderCode());
        order.setTrackingNumber(generateTrackingNumber());
        order.setContactName(draft.getContactName());
        order.setContactPhone(draft.getContactPhone());
        order.setContactEmail(draft.getContactEmail());
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaidAt(OffsetDateTime.now());
        order.setStripePaymentIntentId(paymentIntentId);
        order = orderRepository.save(order);

        BigDecimal wallet = draft.getWalletAmountApplied();
        if (wallet.signum() > 0) {
            GiftCardBalanceApplicationResult drawn = giftCardService.applyBalanceToOrder(customer.getId(), wallet, order);
            if (drawn.totalApplied().compareTo(wallet) < 0) {
                throw new CheckoutFailedException("Your gift card balance changed while you were paying.");
            }
            order.setWalletAmountApplied(drawn.totalApplied());
            order = orderRepository.save(order);
        }

        List<OrderItem> items = new ArrayList<>();
        for (PendingCheckout.Line line : draft.getItems()) {
            OrderItem item = orderMapper.toItemEntity(
                order, products.get(line.productId()), resolveImageUrl(primaryImages, line.productId()),
                line.quantity(), line.deliveryOption());
            // The order is created at what the customer was charged, not at today's price.
            item.setUnitPrice(line.unitPrice());
            item.setSubtotal(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
            items.add(item);
        }
        orderItemRepository.saveAll(items);

        if (draft.getPromoCode() != null && draft.getPromoDiscountAmount().signum() > 0) {
            // Honoured as validated when the draft was made, even if the limit was reached since.
            promoCodeService.recordRedemption(
                customer.getId(), draft.getPromoCode(), draft.getPromoDiscountAmount(), order);
        }

        // Only what was paid for leaves the cart; a Buy Again click made meanwhile keeps its extra quantity.
        if (draft.getCreatedByUserId() == null) {
            cartService.consumePaidQuantities(customer.getId(), draft.getItems());
        }

        pendingCheckoutRepository.delete(draft);

        if (paymentIntentId != null) {
            eventPublisher.publishEvent(new PaymentStatusChangedEvent(
                order.getId(), customer.getId(), PaymentStatus.PENDING, PaymentStatus.PAID, "payment_intent.succeeded"));
        }
        if (actor != null) {
            eventPublisher.publishEvent(new OrderCreatedByAdminEvent(order, actor));
        } else {
            eventPublisher.publishEvent(new OrderPlacedEvent(order, customer));
        }
        return order;
    }

    public Optional<PendingCheckout> findDraft(UUID draftId) {
        return pendingCheckoutRepository.findById(draftId);
    }

    public Optional<PendingCheckout> findDraftByPaymentIntent(String paymentIntentId) {
        return pendingCheckoutRepository.findByStripePaymentIntentId(paymentIntentId);
    }

    public Optional<Order> findOrderByPaymentIntent(String paymentIntentId) {
        return orderRepository.findByStripePaymentIntentId(paymentIntentId);
    }

    public List<PendingCheckout> findCustomerDrafts(UUID userId) {
        return pendingCheckoutRepository.findByUserIdAndCreatedByUserIdIsNull(userId);
    }

    public List<PendingCheckout> findExpiredDrafts(OffsetDateTime cutoff, int limit) {
        return pendingCheckoutRepository.findExpired(cutoff, PageRequest.of(0, limit));
    }

    @Transactional
    public void attachPaymentIntent(UUID draftId, String paymentIntentId) {
        PendingCheckout draft = pendingCheckoutRepository.findByIdForUpdate(draftId)
            .orElseThrow(() -> new ResourceNotFoundException("This checkout was not found or has expired."));
        draft.setStripePaymentIntentId(paymentIntentId);
    }

    @Transactional
    public void discardDraft(UUID draftId) {
        pendingCheckoutRepository.findByIdForUpdate(draftId).ifPresent(pendingCheckoutRepository::delete);
    }

    @Transactional
    public List<Order> adminDeleteOrders(List<UUID> orderIds, User actor) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw BusinessRuleException.invalid("No order ids were provided.");
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
        markCancelled(order, OrderCancelledBy.SYSTEM, "Payment was cancelled");
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
        markCancelled(order, OrderCancelledBy.SYSTEM, "Payment was not completed in time");
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
        order.setTrackingNumber(generateTrackingNumber());

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
            requireOrderable(product); // M-19

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

    private static final String ADMIN_METHOD_CASH = "cash";
    private static final Set<String> ADMIN_PAYMENT_METHODS = Set.of("card", "bank", ADMIN_METHOD_CASH);

    private void validateAdminCreateInput(AdminCreateOrderInput input) {
        if (input.fullName() == null || input.fullName().isBlank()) {
            throw BusinessRuleException.invalid("Customer full name is required for admin order creation");
        }
        if (input.phone() == null || input.phone().isBlank()) {
            throw BusinessRuleException.invalid("Customer phone number is required for admin order creation");
        }
        if (input.fullName().trim().length() > 100) {
            throw BusinessRuleException.invalid("Customer full name must be at most 100 characters");
        }
        if (input.phone().trim().length() > 20) {
            throw BusinessRuleException.invalid("Customer phone number must be at most 20 characters");
        }
        if (input.items() == null || input.items().isEmpty()) {
            throw BusinessRuleException.invalid("At least one item is required");
        }
    }

    // Staff pick how the customer pays: card, bank (same Stripe card form, recorded as "bank") or cash.
    private String requireAdminPaymentMethod(String paymentMethod) {
        String method = paymentMethod == null ? "" : paymentMethod.trim().toLowerCase(Locale.ROOT);
        if (!ADMIN_PAYMENT_METHODS.contains(method)) {
            throw BusinessRuleException.invalid("Payment method must be card, bank or cash");
        }
        return method;
    }

    // An order never exists without an email: the one typed for it, else the customer's account email.
    private String resolveAdminContactEmail(AdminCreateOrderInput input, User customer) {
        String email = input.email() != null && !input.email().isBlank()
            ? input.email().trim() : customer.getEmail();
        if (email == null || email.isBlank()) {
            throw BusinessRuleException.invalid("Customer email is required for admin order creation");
        }
        requireValidEmail(email);
        return email;
    }

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
        throw BusinessRuleException.invalid("userId or email is required to identify an existing customer");
    }

    // M-19: the catalog hides INACTIVE products, but checkout and the admin order forms looked them up by id
    // with findById only, so an unpublished product could still be bought by anyone who knew its id.
    // Checked before reserveStock, so nothing is reserved for a product that can't be sold.
    private void requireOrderable(Product product) {
        if (product.getStatus() != CollectionStatus.ACTIVE) {
            throw BusinessRuleException.invalid("\"" + product.getName() + "\" is no longer available.");
        }
    }

    private void reserveStock(UUID productId, int quantity) {
        int updatedRows = productRepository.decrementStock(productId, quantity);
        if (updatedRows == 0) {
            throw new InsufficientStockException(
                "Not enough stock for product " + productId + " (requested " + quantity + ")"
            );
        }
        publishStockChange(productId, quantity); // M-29
    }

    private void restoreStock(UUID productId, int quantity) {
        if (quantity > 0) {
            productRepository.incrementStock(productId, quantity);
            publishStockChange(productId, -quantity); // M-29
        }
    }

    // M-29 flow: atomic stock UPDATE -> read the new stock back (same transaction, so it includes the update)
    // -> previous = new + quantity taken -> ProductStockChangedEvent -> after commit,
    // ProductNotificationListener alerts staff if the stock crossed the low / out-of-stock line.
    // Sales used to move stock only through these atomic queries, which published nothing, so the stock
    // alerts never fired. A rolled-back order publishes nothing (the listener runs AFTER_COMMIT).
    private void publishStockChange(UUID productId, int quantityTaken) {
        productRepository.findStockLevel(productId).ifPresent(level -> {
            int newStock = level.stockQuantity() != null ? level.stockQuantity() : 0;
            eventPublisher.publishEvent(new ProductStockChangedEvent(
                level.id(), level.name(), newStock + quantityTaken, newStock, level.lowStockThreshold(), null));
        });
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

    // M-16: payment states in which money was (or is being) collected or returned for the current total.
    // Changing the items there would make the order disagree with what the customer paid, with no refund or
    // extra charge behind it.
    private static final Set<PaymentStatus> ITEM_EDIT_LOCKED_PAYMENT_STATUSES = EnumSet.of(
        PaymentStatus.PROCESSED, PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);

    // M-16: called before any item or stock is touched. Checks both the status the order had and the one
    // this same request sets, so "mark PAID and change items" in one call is refused too.
    // An existing Stripe intent also blocks: its amount was fixed when it was created, and createPaymentIntent
    // hands the same intent back, so the customer would pay the old total. Updating the intent amount at
    // Stripe would mean a network call inside this transaction (see M-26), so staff cancel and re-create.
    private void requireItemsEditable(Order order, PaymentStatus previousPaymentStatus) {
        if (ITEM_EDIT_LOCKED_PAYMENT_STATUSES.contains(previousPaymentStatus)
            || ITEM_EDIT_LOCKED_PAYMENT_STATUSES.contains(order.getPaymentStatus())) {
            throw BusinessRuleException.conflict(
                "Items can't be changed after payment. Refund the order in Stripe and create a new one instead.");
        }
        if (order.getStripePaymentIntentId() != null) {
            throw BusinessRuleException.conflict(
                "The customer has already started paying for this order, so its items can't be changed. "
                    + "Cancel it and create a new order instead.");
        }
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

    private String generateOrderCode() {
        Long sequenceValue = orderRepository.getNextOrderCodeSequenceValue();
        return "VS-" + String.format("%08d", sequenceValue);
    }

    // No 0/O/1/I so a code read aloud or off a screenshot can't be mistyped.
    private static final String TRACKING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TRACKING_CODE_LENGTH = 8;
    private static final int TRACKING_MAX_ATTEMPTS = 10;
    private static final SecureRandom TRACKING_RANDOM = new SecureRandom();

    // Random, unique per order. The existsBy check avoids most collisions; the unique index
    // (uq_orders_tracking_number) is the final guard against two concurrent orders drawing the same code.
    String generateTrackingNumber() {
        for (int attempt = 0; attempt < TRACKING_MAX_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder("VT-");
            for (int i = 0; i < TRACKING_CODE_LENGTH; i++) {
                code.append(TRACKING_ALPHABET.charAt(TRACKING_RANDOM.nextInt(TRACKING_ALPHABET.length())));
            }
            String candidate = code.toString();
            if (!orderRepository.existsByTrackingNumber(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not generate a unique tracking number");
    }

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private void requireValidEmail(String email) {
        if (email.length() > 255) {
            throw BusinessRuleException.invalid("Customer email must be at most 255 characters");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw BusinessRuleException.invalid("Customer email is not valid");
        }
    }

    private void markCancelled(Order order, OrderCancelledBy by, String reason) {
        order.setCancelledBy(by);
        order.setCancelledAt(OffsetDateTime.now());
        order.setCancelReason(reason);
    }

    private void clearCancellation(Order order) {
        order.setCancelledBy(null);
        order.setCancelledAt(null);
        order.setCancelReason(null);
    }

    // ---------- Customer cancellation ----------

    // What the cancellation left to do outside the DB transaction: refund this much to the card (null = nothing).
    public record CustomerCancelResult(Order order, String paymentIntentId, BigDecimal cardRefundAmount) {}

    // Whether the customer may cancel: paid, and not yet shipped.
    public static boolean isCancellableByCustomer(Order order) {
        OrderStatus status = order.getOrderStatus();
        return (status == OrderStatus.PLACED || status == OrderStatus.PROCESSING)
            && order.getPaymentStatus() == PaymentStatus.PAID;
    }

    private static final int MAX_CANCEL_NOTE_LENGTH = 150;

    // Only the DB side. The card refund is a Stripe call and is done by OrderCancellationService after
    // this transaction commits, so a slow Stripe call never holds the order row lock (see M-26).
    @Transactional
    public CustomerCancelResult cancelOrderForCustomer(
        UUID userId, UUID orderId, OrderCancelReason reason, String note
    ) {
        Order order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Not your order");
        }
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw BusinessRuleException.conflict("This order is already cancelled.");
        }
        if (!isCancellableByCustomer(order)) {
            throw BusinessRuleException.conflict(
                "This order can no longer be cancelled because it has already shipped or isn't paid.");
        }

        String trimmedNote = note != null ? note.trim() : "";
        if (trimmedNote.length() > MAX_CANCEL_NOTE_LENGTH) {
            throw BusinessRuleException.invalid("Cancel note must be at most " + MAX_CANCEL_NOTE_LENGTH + " characters");
        }
        String storedReason = reason == OrderCancelReason.OTHER && !trimmedNote.isEmpty()
            ? reason.label() + ": " + trimmedNote
            : reason.label();

        OrderStatus previousStatus = order.getOrderStatus();
        order.setOrderStatus(OrderStatus.CANCELLED);
        markCancelled(order, OrderCancelledBy.CUSTOMER, storedReason);
        releaseStock(order);
        giftCardService.restoreWalletForOrder(order); // idempotent
        Order saved = orderRepository.save(order);

        // Only the part Stripe actually charged is refunded to the card; wallet money went back above.
        BigDecimal wallet = saved.getWalletAmountApplied() != null ? saved.getWalletAmountApplied() : BigDecimal.ZERO;
        BigDecimal cardPortion = saved.getTotal().subtract(wallet);
        boolean refundCard = saved.getStripePaymentIntentId() != null && cardPortion.signum() > 0;

        eventPublisher.publishEvent(new OrderCancelledByCustomerEvent(
            saved, saved.getUser(), previousStatus, refundCard ? cardPortion : BigDecimal.ZERO));

        return new CustomerCancelResult(
            saved, saved.getStripePaymentIntentId(), refundCard ? cardPortion : null);
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