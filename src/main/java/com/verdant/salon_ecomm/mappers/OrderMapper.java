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
import com.verdant.salon_ecomm.services.OrderService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class OrderMapper {
    private final OrderService orderService;

    public OrderMapper(OrderService orderService) {
        this.orderService = orderService;
    }

    // ---------- Entity -> DTO ----------

    public AdminOrderDto toAdminDto(Order order, List<OrderItem> items) {
        List<AdminOrderItemDto> itemDtos = items.stream()
            .map(this::toAdminOrderItemDto)
            .toList();

        return new AdminOrderDto(
            order.getId(),
            order.getOrderCode(),
            order.getUser(),
            order.getOrderStatus(),
            order.getPaymentStatus(),
            order.getPaymentMethod(),
            order.getAddress(),
            order.getSubtotal(),
            order.getDeliveryFee(),
            order.getTotal(),
            itemDtos.size(),
            itemDtos,
            buildActivity(order),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }

    public OrderItemDto toOrderItemDto(OrderItem item) {
        String label = "Quantity: " + item.getQuantity()
            + " \u00b7 " + formatPriceFixed(item.getUnitPrice())
            + " \u00b7 " + formatDeliveryOption(item.getDeliveryOption());

        return new OrderItemDto(
            item.getId(),
            item.getProduct(),
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
            + " \u00d7 " + formatPriceCompact(item.getUnitPrice())
            + " \u00b7 " + formatDeliveryOption(item.getDeliveryOption());

        return new AdminOrderItemDto(
            item.getId(),
            item.getProduct(),
            item.getProductName(),
            item.getProductImage(),
            item.getQuantity(),
            item.getUnitPrice(),
            item.getSubtotal(),
            item.getDeliveryOption(),
            label
        );
    }

    // ---------- Automatic activity timeline ----------
    // Order does not persist a timestamp per stage, so this is reconstructed from
    // the current status snapshot (createdAt/updatedAt) rather than true history.

    public List<OrderActivityDto> buildActivity(Order order) {
        List<OrderActivityDto> activity = new ArrayList<>();

        activity.add(new OrderActivityDto("Order placed", order.getCreatedAt()));

        PaymentStatus paymentStatus = order.getPaymentStatus();
        if (paymentStatus == PaymentStatus.PAID || paymentStatus == PaymentStatus.PROCESSED) {
            activity.add(new OrderActivityDto("Payment received", order.getUpdatedAt()));
        } else if (paymentStatus == PaymentStatus.FAILED) {
            activity.add(new OrderActivityDto("Payment failed", order.getUpdatedAt()));
        } else if (paymentStatus == PaymentStatus.REFUNDED) {
            activity.add(new OrderActivityDto("Payment refunded", order.getUpdatedAt()));
        }

        OrderStatus status = order.getOrderStatus();
        if (status == OrderStatus.PROCESSING) {
            activity.add(new OrderActivityDto("Order processing", order.getUpdatedAt()));
        } else if (status == OrderStatus.IN_TRANSIT) {
            activity.add(new OrderActivityDto("Order in transit", order.getUpdatedAt()));
        } else if (status == OrderStatus.DELIVERED) {
            activity.add(new OrderActivityDto("Order delivered", order.getUpdatedAt()));
        } else if (status == OrderStatus.CANCELLED) {
            activity.add(new OrderActivityDto("Order cancelled", order.getUpdatedAt()));
        }

        return activity;
    }

    // ---------- Input -> Entity ----------

    public Order toEntity(
        User user, Address address, String paymentMethod,
        BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal total
    ) {
        return Order.builder()
            .user(user)
            .address(address)
            .paymentMethod(paymentMethod)
            .subtotal(subtotal)
            .deliveryFee(deliveryFee)
            .total(total)
            .orderStatus(OrderStatus.PLACED)
            .paymentStatus(PaymentStatus.PROCESSED)
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
}