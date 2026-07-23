package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.order.OrderActivityDto;
import com.verdant.salon_ecomm.dtos.order.OrderDetailDto;
import com.verdant.salon_ecomm.dtos.order.OrderItemDto;
import com.verdant.salon_ecomm.dtos.order.OrderSummaryDto;
import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.entities.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    // ---- item level ----

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productCategory", source = "product", qualifiedByName = "productCategory")
    OrderItemDto toItemDto(OrderItem item);

    List<OrderItemDto> toItemDtos(List<OrderItem> items);

    // Category label shown in image 1 (SKINCARE/FRAGRANCE-style tag)
    // comes from Product.itemCatalog (ItemCatalog enum: SKIN_CARE,
    // HAIR_CARE, MAKE_UP). Rendered as the enum name; adjust here if
    // you want it prettified (e.g. "SKIN_CARE" -> "Skin Care").
    @Named("productCategory")
    default String productCategory(com.verdant.salon_ecomm.entities.Product product) {
        if (product == null || product.getItemCatalog() == null) {
            return null;
        }
        return product.getItemCatalog().name();
    }

    // ---- summary (list) level ----

    default OrderSummaryDto toSummaryDto(Order order, List<OrderItem> items) {
        List<OrderItemDto> itemDtos = toItemDtos(items);
        return new OrderSummaryDto(
            order.getId(),
            reference(order),
            customerName(order),
            order.getOrderStatus(),
            order.getPaymentStatus(),
            items.size(),
            order.getTotal(),
            order.getCreatedAt(),
            itemDtos
        );
    }

    default OrderDetailDto toDetailDto(Order order, List<OrderItem> items) {
        List<OrderItemDto> itemDtos = toItemDtos(items);
        return new OrderDetailDto(
            order.getId(),
            reference(order),
            customerName(order),
            order.getOrderStatus(),
            order.getPaymentStatus(),
            order.getPaymentMethod(),
            items.size(),
            order.getSubtotal(),
            order.getDeliveryFee(),
            order.getTotal(),
            order.getNotes(),
            order.getCreatedAt(),
            order.getUpdatedAt(),
            itemDtos,
            activity(order),
            shippingAddress(order)
        );
    }

    @Named("reference")
    default String reference(Order order) {
        if (order == null || order.getId() == null) {
            return null;
        }
        String hex = order.getId().toString().replace("-", "").toUpperCase();
        return "VS-" + hex.substring(0, 5);
    }

    @Named("customerName")
    default String customerName(Order order) {
        if (order == null || order.getUser() == null) {
            return null;
        }
        return order.getUser().getFullName();
    }

    @Named("activity")
    default List<OrderActivityDto> activity(Order order) {
        List<OrderActivityDto> activity = new java.util.ArrayList<>();
        if (order.getCreatedAt() != null) {
            activity.add(new OrderActivityDto("Order placed", order.getCreatedAt()));
        }
        if (order.getPaymentStatus() == com.verdant.salon_ecomm.models.enums.PaymentStatus.PROCESSED
            || order.getPaymentStatus() == com.verdant.salon_ecomm.models.enums.PaymentStatus.PAID) {
            activity.add(new OrderActivityDto("Payment received", order.getUpdatedAt()));
        }
        if (order.getPaymentStatus() == com.verdant.salon_ecomm.models.enums.PaymentStatus.REFUNDED) {
            activity.add(new OrderActivityDto("Payment refunded", order.getUpdatedAt()));
        }
        if (order.getPaymentStatus() == com.verdant.salon_ecomm.models.enums.PaymentStatus.FAILED) {
            activity.add(new OrderActivityDto("Payment failed", order.getUpdatedAt()));
        }
        return activity;
    }

    @Named("shippingAddress")
    default OrderDetailDto.AddressDto shippingAddress(Order order) {
        if (order == null) {
            return null;
        }
        return new OrderDetailDto.AddressDto(
            order.getSnapAddressLine1(),
            order.getSnapAddressLine2(),
            order.getSnapAddressCity(),
            order.getSnapAddressState(),
            order.getSnapAddressPostal(),
            order.getSnapAddressCountry()
        );
    }

    default UUID map(UUID value) {
        return value;
    }
}