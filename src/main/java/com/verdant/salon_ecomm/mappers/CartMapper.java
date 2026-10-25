package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.cart.ProductSummaryDto;
import com.verdant.salon_ecomm.models.entities.CartItem;
import com.verdant.salon_ecomm.dtos.cart.CartDto;
import com.verdant.salon_ecomm.dtos.cart.CartItemDto;
import com.verdant.salon_ecomm.models.entities.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class CartMapper {

    public CartItemDto toDto(CartItem entity, Set<UUID> favoritedProductIds) {
        if (entity == null) return null;

        ProductSummaryDto productSummary = null;
        Product product = entity.getProduct();

        if (product != null) {
            String primaryImageUrl = (product.getImages() != null && product.getImages().length > 0)
                ? product.getImages()[0]
                : null;

            productSummary = ProductSummaryDto.builder()
                .id(product.getId())
                .name(product.getName())
                .itemCatalog(product.getItemCatalog())
                .price(product.getPrice())
                // H-4: shown only when it really lowers the charged price (effectivePrice), so a stale or
                // mistyped sale price (zero, or not below the regular price) isn't displayed as a sale.
                .salePrice(product.effectivePrice().compareTo(product.getPrice()) < 0 ? product.effectivePrice() : null)
                .image(primaryImageUrl)
                .isFavorite(favoritedProductIds.contains(product.getId()))
                .build();
        }

        return CartItemDto.builder()
            .id(entity.getId())
            .product(productSummary)
            .quantity(entity.getQuantity())
            .deliveryOption(entity.getDeliveryOption())
            .build();
    }

    // L-17: favoritedProductIds is looked up by CartService; the mapper no longer touches the database.
    public CartDto toCartDto(List<CartItem> items, Set<UUID> favoritedProductIds) {
        List<CartItemDto> itemDTOs = items.stream()
            .map(item -> toDto(item, favoritedProductIds))
            .toList();

        int totalItems = items.stream()
            .mapToInt(CartItem::getQuantity)
            .sum();

        BigDecimal subtotal = items.stream()
            // H-4: same price the order will charge (sale price when it applies), so cart and checkout agree.
            .map(item -> item.getProduct().effectivePrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CartDto.builder()
            .items(itemDTOs)
            .totalItems(totalItems)
            .subtotal(subtotal)
            .build();
    }
}