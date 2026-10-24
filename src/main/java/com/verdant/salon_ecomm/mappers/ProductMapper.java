package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.dtos.product.AdminProductDto;
import com.verdant.salon_ecomm.models.entities.MediaImage;
import com.verdant.salon_ecomm.models.entities.Product;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class ProductMapper {

    public AdminProductDto toAdminDTO(Product product, List<MediaImage> images) {
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

