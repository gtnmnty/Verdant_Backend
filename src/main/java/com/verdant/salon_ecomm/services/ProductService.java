package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.dtos.product.*;
import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.models.enums.CollectionSort;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.specifications.ProductSpec;
import com.verdant.salon_ecomm.utils.EnumUtils;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final MediaImageRepository mediaImageRepository;

    public ProductPage getProducts(
        String category, String search,
        CollectionSort sort, int page, int pageSize
    ) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, toSort(sort));

        Page<Product> result = productRepository.findAll(
            ProductSpec.filter(category, search, CollectionStatus.ACTIVE),
            pageable
        );

        return new ProductPage(
            result.getContent(),
            page,
            pageSize,
            (int) result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public Product getProductDetail(UUID id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        if (product.getStatus() != CollectionStatus.ACTIVE) {
            throw new EntityNotFoundException("Product not found");
        }

        return product;
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

    public AdminProductPage getAdminProducts(
        String category, String search,
        CollectionSort sort,
        CollectionStatus status, int page, int pageSize
    ) {
        Pageable pageable = PageRequest.of(page - 1, pageSize, toSort(sort));

        Page<Product> result = productRepository.findAll(
            ProductSpec.filter(category, search, status),
            pageable
        );

        List<AdminProductDto> items = result.getContent().stream()
            .map(this::toAdminDTO)
            .toList();

        return new AdminProductPage(
            items, page, pageSize,
            (int) result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public AdminProductDto getAdminProduct(UUID id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        return toAdminDTO(product);
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

    @Transactional
    public AdminProductDto createProduct(CreateProductInput input) {
        Product product = Product.builder()
            .name(input.name())
            .itemCatalog(EnumUtils.parseEnum(ItemCatalog.class, input.category(), "category"))
            .description(input.description())
            .price(input.price())
            .salePrice(input.salePrice())
            .sku(input.sku())
            .badge(input.badge())
            .tags(input.tags())
            .info(input.info() != null ? input.info().toArray(new String[0]) : new String[0])
            .stockQuantity(input.stockQuantity())
            .lowStockThreshold(input.lowStockThreshold())
            .isFeatured(input.isFeatured() != null ? input.isFeatured() : false)
            .status(input.status() != null ? input.status() : CollectionStatus.ACTIVE)
            .build();

        return toAdminDTO(productRepository.save(product));
    }

    @Transactional
    public AdminProductDto updateProduct(UpdateProductInput input) {
        Product product = productRepository.findById(UUID.fromString(input.id()))
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        if (input.name() != null) product.setName(input.name());
        if (input.category() != null)
            product.setItemCatalog(EnumUtils.parseEnum(ItemCatalog.class, input.category(), "category"));
        if (input.description() != null) product.setDescription(input.description());
        if (input.price() != null) product.setPrice(input.price());
        if (input.salePrice() != null) product.setSalePrice(input.salePrice());
        if (input.sku() != null) product.setSku(input.sku());
        if (input.badge() != null) product.setBadge(input.badge());
        if (input.tags() != null) product.setTags(input.tags());
        if (input.info() != null) product.setInfo(input.info().toArray(new String[0]));
        if (input.stockQuantity() != null) product.setStockQuantity(input.stockQuantity());
        if (input.lowStockThreshold() != null) product.setLowStockThreshold(input.lowStockThreshold());
        if (input.isFeatured() != null) product.setFeatured(input.isFeatured());
        if (input.status() != null) product.setStatus(input.status());

        return toAdminDTO(productRepository.save(product));
    }

    @Transactional
    public boolean deleteProduct(UUID id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        mediaImageRepository.deleteByEntityTypeAndEntityId(ItemType.PRODUCT, id);
        productRepository.delete(product);
        return true;
    }

}