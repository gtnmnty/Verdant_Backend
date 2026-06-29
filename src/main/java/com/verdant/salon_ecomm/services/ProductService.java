package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.dtos.product.ProductPage;
import com.verdant.salon_ecomm.dtos.product.AdminProductDto;
import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.models.enums.CollectionSort;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.specifications.ProductSpec;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository productRepository;
  private final MediaImageRepository mediaImageRepository;

  public ProductPage getProducts(String category, String search, CollectionSort sort, Boolean isActive, int page, int pageSize) {
    Pageable pageable = PageRequest.of(page - 1, pageSize, toSort(sort));

    Page<Product> result = productRepository.findAll(
            ProductSpec.filter(category, search, isActive),
            pageable
    );

    List<AdminProductDto> items = result.getContent().stream()
            .map(this::toAdminDTO)
            .toList();

    return new ProductPage(items, page, pageSize, (int) result.getTotalElements(), result.getTotalPages());
  }

  public AdminProductDto getProduct(UUID id) {
    Product product = productRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    return toAdminDTO(product);
  }

  private AdminProductDto toAdminDTO(Product product) {
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
            product.getInfo() != null ? List.of(product.getInfo()) : List.of(),
            product.getBadge(),
            product.isFeatured(),
            product.getIsActive(),
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

  private Sort toSort(CollectionSort sort) {
    return switch (sort) {
      case NEWEST -> Sort.by("createdAt").descending();
      case OLDEST -> Sort.by("createdAt").ascending();
      case PRICE_LOW_TO_HIGH -> Sort.by("price").ascending();
      case PRICE_HIGH_TO_LOW -> Sort.by("price").descending();
    };
  }
}
