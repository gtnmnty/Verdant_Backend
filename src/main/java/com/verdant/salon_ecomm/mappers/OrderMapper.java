package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.dtos.order.*;
import com.verdant.salon_ecomm.dtos.order.admin.AdminOrderDto;
import com.verdant.salon_ecomm.dtos.order.admin.AdminOrderItemDto;
import com.verdant.salon_ecomm.dtos.order.admin.AdminOrderUserDto;
import com.verdant.salon_ecomm.models.entities.Address;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.OrderItem;
import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.config.StripeConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Component
public class OrderMapper {

    // Symbol of the store currency (stripe.currency), e.g. "$" for usd, used in item labels.
    private final String currencySymbol;

    public OrderMapper(StripeConfig stripeConfig) {
        this.currencySymbol = Currency.getInstance(stripeConfig.getCurrency().toUpperCase(Locale.ROOT))
            .getSymbol(Locale.US);
    }

    // ---------- Entity -> DTO ----------

    // The customer-facing "Order" type has no DTO: resolvers return the entity and OrderResolver fills
    // items/address (H-12). A second shape for the same GraphQL type is what broke myOrders.

    public AdminOrderDto toAdminDto(Order order, List<OrderItem> items) {
        List<AdminOrderItemDto> itemDtos = items.stream()
            .map(this::toAdminOrderItemDto)
            .toList();

        return new AdminOrderDto(
            order.getId(),
            order.getOrderCode(),
            toAdminOrderUserDto(order),
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
            order.getUpdatedAt(),
            order.getPromoDiscountAmount(),
            order.getWalletAmountApplied()
        );
    }

    // Contact details entered for a staff-placed order win over the account's saved ones.
    private AdminOrderUserDto toAdminOrderUserDto(Order order) {
        User user = order.getUser();
        if (user == null) return null;
        return new AdminOrderUserDto(
            user.getId(),
            order.getContactName() != null ? order.getContactName() : user.getFullName(),
            user.getEmail(),
            order.getContactPhone() != null ? order.getContactPhone() : user.getPhone()
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
            .paymentStatus(PaymentStatus.PENDING)
            .build();
        // orderCode is generated and set by the service, not here
    }

    public OrderItem toItemEntity(
        Order order, Product product, String productImage, int quantity, DeliveryOption deliveryOption
    ) {
        // H-4: charge what the shop displays. Snapshotted on the line, so a later price change doesn't touch it.
        BigDecimal unitPrice = product.effectivePrice();
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
        return currencySymbol + String.format(Locale.US, "%.2f", price);
    }

    private String formatPriceCompact(BigDecimal price) {
        return currencySymbol + price.stripTrailingZeros().toPlainString();
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
            product.getItemCatalog(), // exposed as "catalog"
            product.getPrice()
        );
    }
}