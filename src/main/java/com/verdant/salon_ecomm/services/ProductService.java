package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.verdant.salon_ecomm.dtos.product.*;
import com.verdant.salon_ecomm.dtos.product.events.ProductCreatedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductDeletedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductUpdatedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductsBulkDeletedEvent;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.ProductMapper;
import com.verdant.salon_ecomm.models.entities.MediaImage;
import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.CollectionSort;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.specifications.ProductSpec;
import com.verdant.salon_ecomm.utils.CloudinaryCleanup;
import com.verdant.salon_ecomm.utils.EnumUtils;
import com.verdant.salon_ecomm.utils.PageRequests;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final MediaImageRepository mediaImageRepository;
    private final CloudinaryService cloudinaryService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProductPage getProducts(
        String category, String search,
        CollectionSort sort, int page, int pageSize
    ) {
        int normalizePage = PageRequests.page(page);
        int normalizePageSize = PageRequests.size(pageSize, PageRequests.DEFAULT_MAX_SIZE);

        Pageable pageable = PageRequest.of(normalizePage - 1, normalizePageSize, toSort(sort));

        Page<Product> result = productRepository.findAll(
            ProductSpec.filter(category, search, CollectionStatus.ACTIVE),
            pageable
        );

        return new ProductPage(
            result.getContent(),
            normalizePage,
            normalizePageSize,
            (int) result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public Product getProductDetail(UUID id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.getStatus() != CollectionStatus.ACTIVE) {
            throw new ResourceNotFoundException("Product not found");
        }

        return product;
    }

    public AdminProductPage getAdminProducts(
        String category, String search,
        CollectionSort sort,
        CollectionStatus status, int page, int pageSize
    ) {
        int normalizePage = PageRequests.page(page);
        int normalizePageSize = PageRequests.size(pageSize, PageRequests.DEFAULT_MAX_SIZE);

        Pageable pageable = PageRequest.of(normalizePage - 1, normalizePageSize, toSort(sort));

        Page<Product> result = productRepository.findAll(
            ProductSpec.filter(category, search, status),
            pageable
        );

        List<UUID> productIds = result.getContent().stream()
            .map(Product::getId)
            .toList();

        Map<UUID, List<MediaImage>> imagesByProduct = productIds.isEmpty()
            ? Map.of()
            : mediaImageRepository
            .findByEntityTypeAndEntityIdInOrderBySortOrderAsc(ItemType.PRODUCT, productIds)
            .stream()
            .collect(Collectors.groupingBy(MediaImage::getEntityId));

        List<AdminProductDto> items = result.getContent().stream()
            .map(product -> productMapper.toAdminDTO(
                product, imagesByProduct.getOrDefault(product.getId(), List.of())))
            .toList();

        return new AdminProductPage(
            items, normalizePage, normalizePageSize,
            Math.toIntExact(result.getTotalElements()),
            result.getTotalPages()
        );
    }

    // ------ Mutations -----
    // M-29: create, update and delete now publish ProductCreated/Updated/DeletedEvent with the actor from the
    // resolver. The listeners (ProductAuditListener, ProductNotificationListener) existed, but nothing ever
    // published these events, so the product audit trail was empty and stock alerts never fired.
    @Transactional
    public AdminProductDto createProduct(CreateProductInput input, UUID actorId) {
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
            .reviewCount(0)
            .averageRating(BigDecimal.ZERO)
            .build();

        Product saved = productRepository.save(product);
        eventPublisher.publishEvent(new ProductCreatedEvent(saved, resolveActor(actorId)));
        return productMapper.toAdminDTO(saved, imagesOf(saved));
    }

    // M-15 flow: load (with its @Version) -> apply only the fields sent -> save. Product is @DynamicUpdate,
    // so the UPDATE names only the changed columns: an edit that doesn't touch stock can't write back a
    // stale stock_quantity. If a sale or restock (decrementStock / incrementStock, which bump the version)
    // committed after the load, the UPDATE matches 0 rows and the admin gets a 409 "reload and try again".
    // The admin form sends stockQuantity only when the admin changed it, so an unrelated edit made from a
    // page opened minutes ago doesn't reset the stock to what the page showed.
    // M-29: the stock and status before the change go into ProductUpdatedEvent, so the audit entry can say
    // "12 -> 3" and the notification listener can tell when stock crossed the low / out-of-stock line.
    @Transactional
    public AdminProductDto updateProduct(UpdateProductInput input, UUID actorId) {
        Product product = productRepository.findById(UUID.fromString(input.id()))
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        int previousStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        CollectionStatus previousStatus = product.getStatus();

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

        Product saved = productRepository.save(product);
        int newStock = saved.getStockQuantity() != null ? saved.getStockQuantity() : 0;
        eventPublisher.publishEvent(new ProductUpdatedEvent(
            saved, resolveActor(actorId),
            newStock != previousStock, previousStock,
            saved.getStatus() != previousStatus, previousStatus
        ));
        return productMapper.toAdminDTO(saved, imagesOf(saved));
    }

    @Transactional
    public boolean deleteProduct(UUID id, UUID actorId) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        List<MediaImage> images = mediaImageRepository.findByEntityTypeAndEntityId(ItemType.PRODUCT, id);
        List<String> publicIds = images.stream().map(MediaImage::getPublicId).toList();

        mediaImageRepository.deleteByEntityTypeAndEntityId(ItemType.PRODUCT, id);
        productRepository.delete(product);

        // Cloudinary files are removed only after the transaction commits, same as deleteProducts.
        // The DB delete is only flushed at commit, so an FK failure (cart or order items still pointing
        // at the product) surfaces there. Deleting files first meant a rolled-back delete kept the
        // product but lost its images for good.
        // Tradeoff: a crash right after commit leaves orphaned files in Cloudinary instead of broken images.
        CloudinaryCleanup.scheduleCloudinaryDeletion(publicIds, cloudinaryService::delete);
        eventPublisher.publishEvent(new ProductDeletedEvent(product, resolveActor(actorId))); // M-29
        return true;
    }

    // M-24: returns AdminProductDto, the type the schema promises for deleteProducts. It used to return
    // the entities, whose property names differ from the DTO (itemCatalog vs category), so non-null
    // fields resolved to null and the mutation reported an error although the delete had committed.
    // The DTOs are built before the delete, from the rows and images loaded here, the same way
    // StylistsService.deleteStylists does it.
    @Transactional
    public List<AdminProductDto> deleteProducts(List<UUID> ids, UUID actorId) {
        if (ids == null || ids.isEmpty()) {
            throw BusinessRuleException.invalid("No product ids were provided.");
        }

        List<Product> products = productRepository.findAllById(ids);
        List<UUID> productIds = products.stream().map(Product::getId).toList();

        List<MediaImage> images = mediaImageRepository
            .findByEntityTypeAndEntityIdInOrderBySortOrderAsc(ItemType.PRODUCT, productIds);
        List<String> publicIds = images.stream().map(MediaImage::getPublicId).toList();

        Map<UUID, List<MediaImage>> imagesByProduct = images.stream()
            .collect(Collectors.groupingBy(MediaImage::getEntityId));
        List<AdminProductDto> deleted = products.stream()
            .map(product -> productMapper.toAdminDTO(product, imagesByProduct.getOrDefault(product.getId(), List.of())))
            .toList();

        mediaImageRepository.deleteByEntityTypeAndEntityIdIn(ItemType.PRODUCT, productIds);
        productRepository.deleteAll(products);

        if (!products.isEmpty()) {
            eventPublisher.publishEvent(new ProductsBulkDeletedEvent(products, resolveActor(actorId)));
        }

        CloudinaryCleanup.scheduleCloudinaryDeletion(publicIds, cloudinaryService::delete);

        return deleted;
    }

    public AdminProductDto getAdminProduct(UUID id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        return productMapper.toAdminDTO(product, imagesOf(product));
    }

    private User resolveActor(UUID actorId) {
        return actorId != null ? userRepository.findById(actorId).orElse(null) : null;
    }

    private Sort toSort(CollectionSort sort) {
        CollectionSort safeSort = sort != null ? sort : CollectionSort.NEWEST;

        return switch (safeSort) {
            case NEWEST -> Sort.by("createdAt").descending();
            case OLDEST -> Sort.by("createdAt").ascending();
            case PRICE_LOW_TO_HIGH -> Sort.by("price").ascending();
            case PRICE_HIGH_TO_LOW -> Sort.by("price").descending();
        };
    }

    // Image lookup for single-product responses; list endpoints batch-load images instead.
    private List<MediaImage> imagesOf(Product product) {
        return mediaImageRepository.findByEntityTypeAndEntityIdOrderBySortOrderAsc(ItemType.PRODUCT, product.getId());
    }
}
