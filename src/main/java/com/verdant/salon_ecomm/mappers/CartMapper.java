package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.dtos.cart.ProductSummaryDto;
import com.verdant.salon_ecomm.entities.CartItem;
import com.verdant.salon_ecomm.dtos.cart.CartDto;
import com.verdant.salon_ecomm.dtos.cart.CartItemDto;
import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CartMapper {

    private final FavoriteRepository favoriteRepository;

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
                .salePrice(product.getSalePrice())
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

    public CartDto toCartDto(List<CartItem> items, UUID userId) {
        Set<UUID> favoritedProductIds = getFavoritedProductIds(items, userId);

        List<CartItemDto> itemDTOs = items.stream()
            .map(item -> toDto(item, favoritedProductIds))
            .toList();

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

    private Set<UUID> getFavoritedProductIds(List<CartItem> items, UUID userId) {
        if (userId == null) return Set.of();

        List<UUID> productIds = items.stream()
            .map(CartItem::getProduct)
            .filter(p -> p != null)
            .map(Product::getId)
            .toList();

        if (productIds.isEmpty()) return Set.of();

        return favoriteRepository
            .findByUserIdAndTargetIdInAndTargetType(userId, productIds, ItemType.PRODUCT)
            .stream()
            .map(f -> f.getTargetId())
            .collect(Collectors.toSet());
    }
}