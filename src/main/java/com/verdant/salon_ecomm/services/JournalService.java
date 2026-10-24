package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.journal.*;
import com.verdant.salon_ecomm.dtos.journal.admin.AdminJournalStoryDto;
import com.verdant.salon_ecomm.dtos.journal.admin.AdminJournalStoryPage;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.JournalMapper;
import com.verdant.salon_ecomm.models.entities.MediaImage;
import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.StoredImage;
import com.verdant.salon_ecomm.models.entities.journal.JournalStory;
import com.verdant.salon_ecomm.models.entities.journal.JournalStoryProduct;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.models.enums.journal.JournalCategory;
import com.verdant.salon_ecomm.models.enums.journal.JournalStorySort;
import com.verdant.salon_ecomm.models.enums.journal.JournalStoryStatus;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.repositories.journal.JournalStoryProductRepository;
import com.verdant.salon_ecomm.repositories.journal.JournalStoryRepository;
import com.verdant.salon_ecomm.specifications.JournalStorySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static com.verdant.salon_ecomm.utils.InputValidators.requireText;
import static com.verdant.salon_ecomm.utils.InputValidators.optionalText;
import com.verdant.salon_ecomm.utils.PageRequests;

@Service
@RequiredArgsConstructor
@Slf4j
public class JournalService {

    private static final int MAX_CLIENT_NAME = 100;
    private static final int MAX_QUOTE = 500;
    private static final int MAX_SERVICE = 200;
    private static final int MAX_STYLIST = 100;
    private static final int MAX_SEARCH = 100;
    private static final int MAX_PRODUCTS = 12;
    private static final int MAX_PAGE_SIZE = 50;

    private final JournalStoryRepository journalStoryRepository;
    private final JournalStoryProductRepository journalStoryProductRepository;
    private final ProductRepository productRepository;
    private final MediaImageRepository mediaImageRepository;
    private final CloudinaryService cloudinaryService;
    private final JournalMapper journalMapper;
    private final TransactionTemplate transactionTemplate;

    // ── Public reads (PUBLISHED only) ────────────────────────────

    @Transactional(readOnly = true)
    public JournalStoryPage getPublishedStories(JournalCategory category, String search, int page, int pageSize) {
        Specification<JournalStory> spec = Specification.allOf(
            JournalStorySpec.hasStatus(JournalStoryStatus.PUBLISHED),
            JournalStorySpec.hasCategory(category),
            JournalStorySpec.matchesSearch(cleanSearch(search))
        );
        Pageable pageable = pageable(page, pageSize,
            Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("createdAt")));

        Page<JournalStory> result = journalStoryRepository.findAll(spec, pageable);
        // Inactive products are hidden from the public page (they can't be bought).
        Map<UUID, List<JournalProductDto>> products = loadProducts(idsOf(result.getContent()), true);

        List<JournalStoryDto> items = result.getContent().stream()
            .map(s -> journalMapper.toPublicDto(s, products.getOrDefault(s.getId(), List.of())))
            .toList();
        return new JournalStoryPage(items, pageable.getPageNumber() + 1, pageable.getPageSize(),
            (int) result.getTotalElements(), result.getTotalPages());
    }

    // Returns null for unknown ids AND for drafts - a draft must look exactly like a missing story.
    @Transactional(readOnly = true)
    public JournalStoryDto getPublishedStory(UUID id) {
        return journalStoryRepository.findByIdAndStatus(id, JournalStoryStatus.PUBLISHED)
            .map(story -> journalMapper.toPublicDto(story,
                loadProducts(List.of(story.getId()), true).getOrDefault(story.getId(), List.of())))
            .orElse(null);
    }

    // ── Admin reads ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminJournalStoryPage getAdminStories(
        JournalStoryStatus status, JournalCategory category, String search,
        JournalStorySort sort, int page, int pageSize
    ) {
        Specification<JournalStory> spec = Specification.allOf(
            JournalStorySpec.hasStatus(status),
            JournalStorySpec.hasCategory(category),
            JournalStorySpec.matchesSearch(cleanSearch(search))
        );
        Sort order = sort == JournalStorySort.OLDEST
            ? Sort.by(Sort.Order.asc("createdAt"))
            : Sort.by(Sort.Order.desc("createdAt"));
        Pageable pageable = pageable(page, pageSize, order);

        Page<JournalStory> result = journalStoryRepository.findAll(spec, pageable);
        Map<UUID, List<JournalProductDto>> products = loadProducts(idsOf(result.getContent()), false);

        List<AdminJournalStoryDto> items = result.getContent().stream()
            .map(s -> journalMapper.toAdminDto(s, products.getOrDefault(s.getId(), List.of())))
            .toList();
        return new AdminJournalStoryPage(items, pageable.getPageNumber() + 1, pageable.getPageSize(),
            (int) result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminJournalStoryDto getAdminStory(UUID id) {
        return toAdminDto(findStoryOrThrow(id));
    }

    // ── Create ───────────────────────────────────────────────────
    // Not @Transactional: validate -> upload (network, no DB connection held) -> short DB transaction.
    // If anything fails after an upload, the uploaded files are deleted so Cloudinary isn't left with orphans.

    public AdminJournalStoryDto createStory(
        CreateJournalStoryInput input, MultipartFile resultImage, MultipartFile productImage
    ) {
        String clientName = requireText(input.clientName(), MAX_CLIENT_NAME, "Client name");
        String quote = requireText(input.quote(), MAX_QUOTE, "Quote");
        String service = requireText(input.service(), MAX_SERVICE, "Service");
        String stylist = requireText(input.stylist(), MAX_STYLIST, "Stylist");
        if (input.category() == null) throw new IllegalArgumentException("Category is required");
        List<UUID> productIds = distinctIds(input.productIds());
        JournalStoryStatus status = input.status() != null ? input.status() : JournalStoryStatus.DRAFT;

        if (!hasFile(resultImage)) throw new IllegalArgumentException("A result image is required");
        validateImage(resultImage, "Result image");
        if (hasFile(productImage)) validateImage(productImage, "Product image");

        StoredImage uploadedResult = null;
        StoredImage uploadedProduct = null;
        try {
            uploadedResult = upload(resultImage);
            if (hasFile(productImage)) uploadedProduct = upload(productImage);

            final StoredImage resultFinal = uploadedResult;
            final StoredImage productFinal = uploadedProduct;
            return transactionTemplate.execute(tx -> {
                List<Product> products = loadProductsInOrder(productIds);
                JournalStory story = JournalStory.builder()
                    .clientName(clientName)
                    .quote(quote)
                    .category(input.category())
                    .service(service)
                    .stylist(stylist)
                    .status(status)
                    .resultImage(resultFinal)
                    .productImage(productFinal)
                    .publishedAt(status == JournalStoryStatus.PUBLISHED ? OffsetDateTime.now() : null)
                    .build();
                story.replaceProducts(products);
                return toAdminDto(journalStoryRepository.save(story));
            });
        } catch (RuntimeException e) {
            deleteQuietly(uploadedResult);
            deleteQuietly(uploadedProduct);
            throw e;
        }
    }

    // ── Update ───────────────────────────────────────────────────

    public AdminJournalStoryDto updateStory(
        UUID id, UpdateJournalStoryInput input, MultipartFile resultImage, MultipartFile productImage
    ) {
        if (!journalStoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Journal story not found: " + id); // before any upload
        }

        // null = leave unchanged; a provided value must be valid.
        String clientName = optionalText(input.clientName(), MAX_CLIENT_NAME, "Client name");
        String quote = optionalText(input.quote(), MAX_QUOTE, "Quote");
        String service = optionalText(input.service(), MAX_SERVICE, "Service");
        String stylist = optionalText(input.stylist(), MAX_STYLIST, "Stylist");
        List<UUID> productIds = input.productIds() == null ? null : distinctIds(input.productIds());

        if (hasFile(resultImage)) validateImage(resultImage, "Result image");
        if (hasFile(productImage)) validateImage(productImage, "Product image");

        StoredImage newResult = null;
        StoredImage newProduct = null;
        List<StoredImage> replaced = new ArrayList<>();
        AdminJournalStoryDto updated;
        try {
            if (hasFile(resultImage)) newResult = upload(resultImage);
            if (hasFile(productImage)) newProduct = upload(productImage);

            final StoredImage resultFinal = newResult;
            final StoredImage productFinal = newProduct;
            updated = transactionTemplate.execute(tx -> {
                JournalStory story = findStoryOrThrow(id);

                if (clientName != null) story.setClientName(clientName);
                if (quote != null) story.setQuote(quote);
                if (service != null) story.setService(service);
                if (stylist != null) story.setStylist(stylist);
                if (input.category() != null) story.setCategory(input.category());
                if (productIds != null) story.replaceProducts(loadProductsInOrder(productIds));

                if (resultFinal != null) {
                    replaced.add(story.getResultImage());
                    story.setResultImage(resultFinal);
                }
                if (productFinal != null) {
                    if (story.getProductImage() != null) replaced.add(story.getProductImage());
                    story.setProductImage(productFinal);
                }
                return toAdminDto(journalStoryRepository.save(story));
            });
        } catch (RuntimeException e) {
            deleteQuietly(newResult);
            deleteQuietly(newProduct);
            throw e;
        }

        // Only after the commit succeeded: the replaced images are no longer referenced by anything.
        replaced.forEach(this::deleteQuietly);
        return updated;
    }

    // ── Publish / unpublish ──────────────────────────────────────

    // Moving DRAFT -> PUBLISHED stamps publishedAt = now (so it appears at the top of the Journal).
    @Transactional
    public AdminJournalStoryDto setStatus(UUID id, JournalStoryStatus status) {
        if (status == null) throw new IllegalArgumentException("Status is required");
        JournalStory story = findStoryOrThrow(id);

        if (status == JournalStoryStatus.PUBLISHED && story.getStatus() != JournalStoryStatus.PUBLISHED) {
            story.setPublishedAt(OffsetDateTime.now());
        }
        story.setStatus(status);
        return toAdminDto(journalStoryRepository.save(story));
    }

    // ── Delete ───────────────────────────────────────────────────

    public boolean deleteStory(UUID id) {
        List<StoredImage> images = new ArrayList<>();
        transactionTemplate.executeWithoutResult(tx -> {
            JournalStory story = findStoryOrThrow(id);
            if (story.getResultImage() != null) images.add(story.getResultImage());
            if (story.getProductImage() != null) images.add(story.getProductImage());
            journalStoryRepository.delete(story); // cascades to the product links
        });
        images.forEach(this::deleteQuietly);      // after commit
        return true;
    }

    // ── Helpers ──────────────────────────────────────────────────

    private JournalStory findStoryOrThrow(UUID id) {
        return journalStoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Journal story not found: " + id));
    }

    private AdminJournalStoryDto toAdminDto(JournalStory story) {
        // Admin sees every linked product, including inactive ones, so they can fix them.
        Map<UUID, List<JournalProductDto>> products = loadProducts(List.of(story.getId()), false);
        return journalMapper.toAdminDto(story, products.getOrDefault(story.getId(), List.of()));
    }

    private List<UUID> idsOf(List<JournalStory> stories) {
        return stories.stream().map(JournalStory::getId).toList();
    }

    // Products for many stories in two queries total (links+products, then images), keyed by story id.
    private Map<UUID, List<JournalProductDto>> loadProducts(Collection<UUID> storyIds, boolean activeOnly) {
        if (storyIds.isEmpty()) return Map.of();

        List<JournalStoryProduct> links = journalStoryProductRepository.findAllWithProductByStoryIds(storyIds);
        if (activeOnly) {
            links = links.stream()
                .filter(l -> l.getProduct().getStatus() == CollectionStatus.ACTIVE)
                .toList();
        }

        Set<UUID> productIds = links.stream().map(l -> l.getProduct().getId()).collect(Collectors.toSet());
        Map<UUID, String> imageUrls = productImageUrls(productIds);

        Map<UUID, List<JournalProductDto>> byStory = new HashMap<>();
        for (JournalStoryProduct link : links) {   // already ordered by sortOrder
            Product product = link.getProduct();
            byStory.computeIfAbsent(link.getStory().getId(), k -> new ArrayList<>())
                .add(journalMapper.toProductDto(product, imageUrls.get(product.getId())));
        }
        return byStory;
    }

    // Primary image per product, falling back to the first image by sort order. One query.
    private Map<UUID, String> productImageUrls(Collection<UUID> productIds) {
        if (productIds.isEmpty()) return Map.of();
        List<MediaImage> images = mediaImageRepository
            .findByEntityTypeAndEntityIdInOrderBySortOrderAsc(ItemType.PRODUCT, productIds);

        Map<UUID, String> urls = new HashMap<>();
        for (MediaImage image : images) {
            if (image.isPrimary()) urls.put(image.getEntityId(), image.getUrl());
        }
        for (MediaImage image : images) {
            urls.putIfAbsent(image.getEntityId(), image.getUrl());
        }
        return urls;
    }

    private List<Product> loadProductsInOrder(List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        Map<UUID, Product> found = productRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        List<Product> ordered = new ArrayList<>();
        for (UUID id : ids) {
            Product product = found.get(id);
            if (product == null) throw new IllegalArgumentException("One or more selected products no longer exist");
            ordered.add(product);
        }
        return ordered;
    }

    private List<UUID> distinctIds(List<UUID> ids) {
        if (ids == null) return List.of();
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.size() > MAX_PRODUCTS) {
            throw new IllegalArgumentException("A story can feature at most " + MAX_PRODUCTS + " products");
        }
        return distinct;
    }

    private Pageable pageable(int page, int pageSize, Sort sort) {
        return PageRequests.of(page, pageSize, MAX_PAGE_SIZE, sort);
    }

    private String cleanSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String trimmed = search.trim();
        return trimmed.length() > MAX_SEARCH ? trimmed.substring(0, MAX_SEARCH) : trimmed;
    }

    private boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private void validateImage(MultipartFile file, String label) {
        String type = file.getContentType();
        // SVG is rejected on purpose: it can carry scripts and is served back from a public URL.
        if (type == null || !type.startsWith("image/") || type.equals("image/svg+xml")) {
            throw new IllegalArgumentException(label + " must be an image file (JPEG, PNG, WebP or AVIF)");
        }
    }

    private StoredImage upload(MultipartFile file) {
        CloudinaryService.CloudinaryImageResult result = cloudinaryService.uploadWithDimensions(file);
        if (result.url() == null || result.publicId() == null) {
            throw new IllegalStateException("Image upload returned no url");
        }
        return new StoredImage(result.url(), result.publicId(), result.width(), result.height());
    }

    private void deleteQuietly(StoredImage image) {
        if (image == null || image.getPublicId() == null) return;
        try {
            cloudinaryService.delete(image.getPublicId());
        } catch (Exception e) {
            log.warn("Could not delete Cloudinary image {} (orphaned)", image.getPublicId(), e);
        }
    }
}
