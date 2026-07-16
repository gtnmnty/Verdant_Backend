package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.cart.ProductSummaryDto;
import com.verdant.salon_ecomm.entities.CartItem;
import com.verdant.salon_ecomm.dtos.cart.CartDto;
import com.verdant.salon_ecomm.dtos.cart.CartItemDto;
import com.verdant.salon_ecomm.entities.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class CartMapper {

    public CartItemDto toDto(CartItem entity) {
        if (entity == null) return null;

        ProductSummaryDto productSummary = null;
        Product product = entity.getProduct();

        if (product != null) {
            productSummary = ProductSummaryDto.builder()
                .id(product.getId())
                .name(product.getName())
                .itemCatalog(product.getItemCatalog())
                .price(product.getPrice())
                .salePrice(product.getSalePrice())
                .image(null)
                .isFavorite(false)
                .build();
        }

        return CartItemDto.builder()
            .id(entity.getId())
            .product(productSummary)
            .quantity(entity.getQuantity())
            .deliveryOption(entity.getDeliveryOption())
            .build();
    }

    public CartDto toCartDto(List<CartItem> items) {
        List<CartItemDto> itemDTOs = items.stream().map(this::toDto).toList();

        int totalItems = items.stream()
            .mapToInt(CartItem::getQuantity)
            .sum();

        BigDecimal subtotal = items.stream()
            .map(item -> item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartDto.builder()
            .items(itemDTOs)
            .totalItems(totalItems)
            .subtotal(subtotal)
            .build();
    }
}