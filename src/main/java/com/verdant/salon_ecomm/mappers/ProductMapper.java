package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.dtos.product.AdminProductDto;
import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final MediaImageRepository mediaImageRepository;

    public AdminProductDto toAdminDTO(Product product) {
        List<MediaImage> images = mediaImageRepository
            .findByEntityTypeAndEntityIdOrderBySortOrderAsc(ItemType.PRODUCT, product.getId());

        return new AdminProductDto(
            product.getId().toString(),
            product.getName(),
            product.getItemCatalog().name(),
            product.getDescription(),
            product.getPrice(),
            product.getSalePrice(),
            product.getSku(),
            images.stream().map(this::toImageDTO).toList(),
            product.getTags() != null ? product.getTags() : List.of(),
            product.getInfo() != null ? Arrays.asList(product.getInfo()) : List.of(),
            product.getBadge(),
            product.isFeatured(),
            product.getStatus(),
            product.getStockQuantity(),
            product.getLowStockThreshold(),
            product.getReviewCount(),
            product.getAverageRating(),
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }

    private MediaImageDto toImageDTO(MediaImage image) {
        return new MediaImageDto(
            image.getId().toString(),
            image.getUrl(),
            image.getPublicId(),
            image.isPrimary(),
            image.getSortOrder()
        );
    }
}

