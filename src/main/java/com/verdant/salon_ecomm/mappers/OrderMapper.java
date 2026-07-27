package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.dtos.order.*;
import com.verdant.salon_ecomm.entities.Address;
import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.entities.OrderItem;
import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class OrderMapper {

    // ---------- Entity -> DTO ----------

    public OrderDto toDto(Order order, List<OrderItem> items) {
        List<OrderItemDto> itemDtos = items.stream()
            .map(this::toOrderItemDto)
            .toList();

        return new OrderDto(
            order.getId(),
            order.getOrderCode(),
            order.getOrderStatus(),
            order.getPaymentStatus(),
            order.getPaymentMethod(),
            order.getSubtotal(),
            order.getDeliveryFee(),
            order.getTotal(),
            itemDtos,
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }

    public AdminOrderDto toAdminDto(Order order, List<OrderItem> items) {
        List<AdminOrderItemDto> itemDtos = items.stream()
            .map(this::toAdminOrderItemDto)
            .toList();

        return new AdminOrderDto(
            order.getId(),
            order.getOrderCode(),
            toAdminOrderUserDto(order.getUser()),
            order.getOrderStatus(),
            order.getPaymentStatus(),
            order.getPaymentMethod(),
            order.getShippingAddress(),
            order.getSubtotal(),
            order.getDeliveryFee(),
            order.getTotal(),
            itemDtos.size(),
            itemDtos,
            buildMilestones(order),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }

    private AdminOrderUserDto toAdminOrderUserDto(User user) {
        if (user == null) return null;
        // ASSUMPTION: matches the field names on AdminOrderUserDto - adjust
        // getters to your actual User entity API.
        return new AdminOrderUserDto(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getPhone()
        );
    }

    public OrderItemDto toOrderItemDto(OrderItem item) {
        String label = "Quantity: " + item.getQuantity()
            + " · " + formatPriceFixed(item.getUnitPrice())
            + " · " + formatDeliveryOption(item.getDeliveryOption());

        return new OrderItemDto(
            item.getId(),
            toOrderItemProductDto(item.getProduct()),
            item.getProductName(),
            item.getProductImage(),
            item.getQuantity(),
            item.getUnitPrice(),
            item.getSubtotal(),
            item.getDeliveryOption(),
            label
        );
    }

    public AdminOrderItemDto toAdminOrderItemDto(OrderItem item) {
        String label = "Qty " + item.getQuantity()
            + " × " + formatPriceCompact(item.getUnitPrice())
            + " · " + formatDeliveryOption(item.getDeliveryOption());

        return new AdminOrderItemDto(
            item.getId(),
            toOrderItemProductDto(item.getProduct()),
            item.getProductName(),
            item.getProductImage(),
            item.getQuantity(),
            item.getUnitPrice(),
            item.getSubtotal(),
            item.getDeliveryOption(),
            label
        );
    }

    // ---------- Current-status milestones ----------
    // IMPORTANT: Order does not persist a timestamp per transition, so this is
    // NOT a true activity history/audit log - it's a snapshot of the order's
    // *current* payment/fulfillment status, each entry stamped with the same
    // order.updatedAt. Do not present this to users as a timeline of past
    // events (e.g. "payment received on X, then shipped on Y" would be
    // misleading if both reuse the same updatedAt). If real per-transition
    // history is needed later, this should be replaced with a persisted
    // order/audit-event table and this method should read from that instead.
    public List<OrderMilestoneDto> buildMilestones(Order order) {
        List<OrderMilestoneDto> milestones = new ArrayList<>();

        milestones.add(new OrderMilestoneDto("Order placed", order.getCreatedAt()));

        PaymentStatus paymentStatus = order.getPaymentStatus();
        if (paymentStatus == PaymentStatus.PAID || paymentStatus == PaymentStatus.PROCESSED) {
            milestones.add(new OrderMilestoneDto("Payment received", order.getUpdatedAt()));
        } else if (paymentStatus == PaymentStatus.FAILED) {
            milestones.add(new OrderMilestoneDto("Payment failed", order.getUpdatedAt()));
        } else if (paymentStatus == PaymentStatus.REFUNDED) {
            milestones.add(new OrderMilestoneDto("Payment refunded", order.getUpdatedAt()));
        }

        OrderStatus status = order.getOrderStatus();
        if (status == OrderStatus.PROCESSING) {
            milestones.add(new OrderMilestoneDto("Order processing", order.getUpdatedAt()));
        } else if (status == OrderStatus.IN_TRANSIT) {
            milestones.add(new OrderMilestoneDto("Order in transit", order.getUpdatedAt()));
        } else if (status == OrderStatus.DELIVERED) {
            milestones.add(new OrderMilestoneDto("Order delivered", order.getUpdatedAt()));
        } else if (status == OrderStatus.CANCELLED) {
            milestones.add(new OrderMilestoneDto("Order cancelled", order.getUpdatedAt()));
        }

        return milestones;
    }

    // ---------- Input -> Entity ----------

    public Order toEntity(
        User user, Address address, String paymentMethod,
        BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal total
    ) {
        return Order.builder()
            .user(user)
            .shippingAddress(address)
            .paymentMethod(paymentMethod)
            .subtotal(subtotal)
            .deliveryFee(deliveryFee)
            .total(total)
            .orderStatus(OrderStatus.PLACED)
            // ASSUMPTION: PaymentStatus.PENDING is the "not yet charged" value -
            // rename to match whatever your enum actually calls it. Order should
            // NOT start as PROCESSED; that should only be set once Stripe
            // confirms the PaymentIntent (via webhook or synchronous check).
            .paymentStatus(PaymentStatus.PENDING)
            .build();
        // orderCode is generated and set by the service, not here
    }

    public OrderItem toItemEntity(
        Order order, Product product, String productImage, int quantity, DeliveryOption deliveryOption
    ) {
        BigDecimal unitPrice = product.getPrice();
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

        return OrderItem.builder()
            .order(order)
            .product(product)
            .productName(product.getName())
            .productImage(productImage)
            .quantity(quantity)
            .unitPrice(unitPrice)
            .subtotal(subtotal)
            .deliveryOption(deliveryOption)
            .build();
    }

    public Address fromAddressInput(AddressInput input) {
        if (input == null) return null;
        return Address.builder()
            .line1(input.line1())
            .line2(input.line2())
            .city(input.city())
            .state(input.state())
            .postal(input.postal())
            .country(input.country())
            .build();
    }

    // ---------- Formatting helpers ----------

    private String formatPriceFixed(BigDecimal price) {
        return String.format(Locale.US, "$%.2f", price);
    }

    private String formatPriceCompact(BigDecimal price) {
        return "$" + price.stripTrailingZeros().toPlainString();
    }

    private String formatDeliveryOption(DeliveryOption option) {
        if (option == null) return "";
        String[] words = option.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.US));
        }
        return sb.toString();
    }


    private OrderItemProductDto toOrderItemProductDto(Product product) {
        if (product == null) return null; // product may have been deleted after the order was placed
        return new OrderItemProductDto(
            product.getId(),
            product.getName(),
            product.getItemCatalog(),
            product.getPrice()
        );
    }
}