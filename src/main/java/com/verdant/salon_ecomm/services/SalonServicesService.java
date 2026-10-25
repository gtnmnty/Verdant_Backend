package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.verdant.salon_ecomm.dtos.CatalogItemConnection;
import com.verdant.salon_ecomm.dtos.PageInfo;
import com.verdant.salon_ecomm.dtos.service.*;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceCreatedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceDeletedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceUpdatedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServicesBulkDeletedEvent;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.SalonServiceMapper;
import com.verdant.salon_ecomm.models.entities.*;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.models.enums.ServiceSort;
import com.verdant.salon_ecomm.repositories.*;
import com.verdant.salon_ecomm.specifications.ServiceSpec;
import com.verdant.salon_ecomm.utils.CloudinaryCleanup;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import com.verdant.salon_ecomm.utils.PageRequests;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Validated
public class SalonServicesService {

    private final SalonServiceRepository serviceRepository;
    private final MediaImageRepository mediaImageRepository;
    private final StylistRepository stylistRepository;
    private final CloudinaryService cloudinaryService;
    private final UserRepository userRepository;
    private final FavoriteRepository favoriteRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SalonServiceMapper serviceMapper;
    private final TransactionTemplate transactionTemplate;

    public ServicePage getSalonServices(
        String category, String search, ServiceSort sort,
        int page, int pageSize
    ) {
        int normalizePage = PageRequests.page(page);
        int normalizePageSize = PageRequests.size(pageSize, PageRequests.DEFAULT_MAX_SIZE);

        Pageable pageable = PageRequest.of(normalizePage - 1, normalizePageSize, toSort(sort));

        Page<SalonService> result = serviceRepository.findAll(
            ServiceSpec.filterSalonService(category, search, CollectionStatus.ACTIVE),
            pageable
        );

        return new ServicePage(
            result.getContent(),
            normalizePage,
            normalizePageSize,
            (int) result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public SalonService getServiceDetail(UUID id) {
        SalonService service = serviceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        if (service.getStatus() != CollectionStatus.ACTIVE) {
            throw new ResourceNotFoundException("Service not found");
        }

        return service;
    }

    public CatalogItemConnection getFavoriteServices(UUID userId, int first, String after) {
        UUID cursorId = (after != null && !after.isBlank())
            ? UUID.fromString(new String(Base64.getDecoder().decode(after)))
            : null;

        List<Favorite> favorites = favoriteRepository
            .findByUserIdAndTargetType(userId, ItemType.SALON_SERVICE);

        List<UUID> favoriteTargetIds = favorites.stream()
            .map(Favorite::getTargetId)
            .toList();

        Map<UUID, SalonService> serviceById = favoriteTargetIds.isEmpty()
            ? Map.of()
            : serviceRepository.findAllById(favoriteTargetIds).stream()
            .filter(service -> service.getStatus() == CollectionStatus.ACTIVE)
            .collect(Collectors.toMap(SalonService::getId, service -> service));

        List<UUID> serviceIds = serviceById.keySet().stream()
            .sorted()
            .filter(id -> cursorId == null || id.compareTo(cursorId) > 0)
            .toList();

        if (first < 0) {
            throw BusinessRuleException.invalid("first must be non-negative");
        }

        List<UUID> pageIds = serviceIds.stream().limit(first).toList();
        boolean hasNextPage = serviceIds.size() > pageIds.size();

        List<SalonService> services = pageIds.stream()
            .map(serviceById::get)
            .toList();

        String endCursor = pageIds.isEmpty()
            ? null
            : Base64.getEncoder().encodeToString(pageIds.getLast().toString().getBytes());

        return new CatalogItemConnection(
            new ArrayList<>(services),
            new PageInfo(hasNextPage, endCursor)
        );
    }

    public AdminServicePage getAdminServices(
        String category, String search, ServiceSort sort,
        CollectionStatus status,
        int page, int pageSize
    ) {
        int normalizePage = PageRequests.page(page);
        int normalizePageSize = PageRequests.size(pageSize, PageRequests.DEFAULT_MAX_SIZE);

        Pageable pageable = PageRequest.of(normalizePage - 1, normalizePageSize, toSort(sort));

        Page<SalonService> result = serviceRepository.findAll(
            ServiceSpec.filterSalonService(category, search, status),
            pageable
        );

        // Batch-fetch all images for this page's services in a single query,
        // instead of querying mediaImageRepository once per service (N+1 fix).
        List<UUID> serviceIds = result.getContent().stream()
            .map(SalonService::getId)
            .toList();

        Map<UUID, List<MediaImage>> imagesByServiceId = serviceIds.isEmpty()
            ? Map.of()
            : mediaImageRepository
            .findByEntityTypeAndEntityIdInOrderBySortOrderAsc(ItemType.SALON_SERVICE, serviceIds)
            .stream()
            .collect(Collectors.groupingBy(MediaImage::getEntityId));

        List<AdminServiceDto> services = result.getContent().stream()
            .map(service -> serviceMapper.toAdminDto(
                service,
                imagesByServiceId.getOrDefault(service.getId(), List.of())
            ))
            .toList();

        return new AdminServicePage(
            services, normalizePage, normalizePageSize,
            (int) result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public AdminServiceDto getAdminServiceDto(UUID id) {
        SalonService service = serviceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        return serviceMapper.toAdminDto(service, imagesOf(service));
    }

    // ---------- Mutations ----------
    @Transactional
    public AdminServiceDto createServiceInput(CreateServiceInput input, UUID actorId) {
        List<Stylist> stylists = resolveStylists(input.stylistIds());

        SalonService service = SalonService.builder()
            .name(input.name())
            .subName(input.subName())
            .itemCatalog(ItemCatalog.valueOf(input.category()))
            .durationMinutes(input.durationInMinutes())
            .price(input.price())
            .description(input.description())
            .status(input.status() != null ? input.status() : CollectionStatus.ACTIVE)
            .info(input.info())
            .tags(input.tags())
            .badge(input.badge())
            .isHomeService(input.isHomeService())
            .isFeatured(input.isFeatured())
            .stylists(stylists)
            .reviewCount(0)
            .averageRating(BigDecimal.ZERO)
            .build();

        SalonService saved = serviceRepository.save(service);

        User actor = resolveActor(actorId);
        eventPublisher.publishEvent(new SalonServiceCreatedEvent(saved, actor));

        return serviceMapper.toAdminDto(saved, imagesOf(saved));
    }

    // M-26: NOT_SUPPORTED overrides the class-level read-only transaction, so no DB connection is held
    // while Cloudinary copies the images (one network round trip per image). Same pattern as
    // JournalService.createStory. Flow:
    //   1. source exists? + read its image rows (each a short read of its own; MediaImage has only
    //      plain columns, so nothing lazy is touched outside a transaction)
    //   2. copy every image in Cloudinary, no DB connection held. The copy gets its own files, so
    //      deleting one service never breaks the other's images
    //   3. one short TransactionTemplate: reload the source (its stylists are lazy), save the copy and
    //      its image rows, publish the event
    //   4. if step 2 or 3 fails, the copies already made are deleted, so Cloudinary keeps no orphans
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminServiceDto duplicateService(UUID sourceId, UUID actorId) {
        if (!serviceRepository.existsById(sourceId)) {
            throw new ResourceNotFoundException("Service not found");
        }
        List<MediaImage> sourceImages = mediaImageRepository
            .findByEntityTypeAndEntityIdOrderBySortOrderAsc(ItemType.SALON_SERVICE, sourceId);

        // Same order as sourceImages, so index i is the copy of image i.
        List<CloudinaryService.CloudinaryUploadResult> uploads = new ArrayList<>();
        try {
            for (MediaImage image : sourceImages) {
                uploads.add(cloudinaryService.uploadFromUrl(image.getUrl()));
            }

            return transactionTemplate.execute(tx -> {
                SalonService source = serviceRepository.findById(sourceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

                SalonService copy = SalonService.builder()
                    .name(source.getName() + " (Copy)")
                    .subName(source.getSubName())
                    .itemCatalog(source.getItemCatalog())
                    .durationMinutes(source.getDurationMinutes())
                    .price(source.getPrice())
                    .description(source.getDescription())
                    .status(CollectionStatus.INACTIVE) // never go live until an admin reviews it
                    .info(source.getInfo() != null ? new ArrayList<>(source.getInfo()) : new ArrayList<>())
                    .tags(source.getTags() != null ? new ArrayList<>(source.getTags()) : null)
                    .badge(source.getBadge())
                    .isHomeService(source.getIsHomeService())
                    .isFeatured(false) // a featured original shouldn't silently produce a second featured item
                    .stylists(new ArrayList<>(source.getStylists()))
                    .reviewCount(0)
                    .averageRating(BigDecimal.ZERO)
                    .build();

                SalonService saved = serviceRepository.save(copy);

                List<MediaImage> copiedImages = new ArrayList<>();
                for (int i = 0; i < sourceImages.size(); i++) {
                    MediaImage image = sourceImages.get(i);
                    CloudinaryService.CloudinaryUploadResult uploaded = uploads.get(i);
                    copiedImages.add(MediaImage.builder()
                        .entityType(ItemType.SALON_SERVICE)
                        .entityId(saved.getId())
                        .url(uploaded.url())
                        .publicId(uploaded.publicId())
                        .isPrimary(image.isPrimary())
                        .sortOrder(image.getSortOrder())
                        .build());
                }
                mediaImageRepository.saveAll(copiedImages);

                User actor = resolveActor(actorId);
                eventPublisher.publishEvent(new SalonServiceCreatedEvent(saved, actor));

                return serviceMapper.toAdminDto(saved, copiedImages);
            });
        } catch (RuntimeException e) {
            for (CloudinaryService.CloudinaryUploadResult uploaded : uploads) {
                try {
                    cloudinaryService.delete(uploaded.publicId()); // don't strand half-copied uploads
                } catch (RuntimeException cleanupEx) {
                    e.addSuppressed(cleanupEx);
                }
            }
            throw e;
        }
    }

    @Transactional
    public AdminServiceDto updateServiceInput(UpdateServiceInput input, UUID actorId) {
        SalonService service = serviceRepository.findById(input.id())
            .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        String previousName = service.getName();
        String previousSubName = service.getSubName();
        ItemCatalog previousCatalog = service.getItemCatalog();
        BigDecimal previousPrice = service.getPrice();
        Integer previousDuration = service.getDurationMinutes();
        CollectionStatus previousStatus = service.getStatus();
        Boolean previousIsHomeService = service.getIsHomeService();
        Boolean previousIsFeatured = service.isFeatured();
        Set<UUID> previousStylistIds = service.getStylists().stream().map(Stylist::getId).collect(Collectors.toSet());

        if (input.name() != null) service.setName(input.name());
        if (input.subName() != null) service.setSubName(input.subName());
        if (input.category() != null) service.setItemCatalog(ItemCatalog.valueOf(input.category()));
        if (input.price() != null) service.setPrice(input.price());
        if (input.durationInMinutes() != null) service.setDurationMinutes(input.durationInMinutes());
        if (input.description() != null) service.setDescription(input.description());
        if (input.status() != null) service.setStatus(input.status());
        if (input.info() != null) service.setInfo(input.info());
        if (input.tags() != null) service.setTags(input.tags());
        if (input.badge() != null) service.setBadge(input.badge());
        if (input.isHomeService() != null) service.setIsHomeService(input.isHomeService());
        if (input.isFeatured() != null) service.setFeatured(input.isFeatured());
        if (input.stylistIds() != null) service.setStylists(resolveStylists(input.stylistIds()));

        SalonService saved = serviceRepository.save(service);

        List<SalonServiceUpdatedEvent.FieldChange> changes = new java.util.ArrayList<>();
        if (!Objects.equals(previousName, saved.getName())) {
            changes.add(new SalonServiceUpdatedEvent.FieldChange("name", previousName, saved.getName()));
        }
        if (!Objects.equals(previousSubName, saved.getSubName())) {
            changes.add(new SalonServiceUpdatedEvent.FieldChange("subName", previousSubName, saved.getSubName()));
        }
        if (!Objects.equals(previousCatalog, saved.getItemCatalog())) {
            changes.add(new SalonServiceUpdatedEvent.FieldChange(
                "category", String.valueOf(previousCatalog), String.valueOf(saved.getItemCatalog())
            ));
        }
        if (!Objects.equals(previousPrice, saved.getPrice())) {
            changes.add(new SalonServiceUpdatedEvent.FieldChange(
                "price", String.valueOf(previousPrice), String.valueOf(saved.getPrice())
            ));
        }
        if (!Objects.equals(previousDuration, saved.getDurationMinutes())) {
            changes.add(new SalonServiceUpdatedEvent.FieldChange(
                "durationMinutes", String.valueOf(previousDuration), String.valueOf(saved.getDurationMinutes())
            ));
        }
        if (!Objects.equals(previousStatus, saved.getStatus())) {
            changes.add(new SalonServiceUpdatedEvent.FieldChange(
                "status", String.valueOf(previousStatus), String.valueOf(saved.getStatus())
            ));
        }
        if (!Objects.equals(previousIsHomeService, saved.getIsHomeService())) {
            changes.add(new SalonServiceUpdatedEvent.FieldChange(
                "isHomeService", String.valueOf(previousIsHomeService), String.valueOf(saved.getIsHomeService())
            ));
        }
        if (!Objects.equals(previousIsFeatured, saved.isFeatured())) {
            changes.add(new SalonServiceUpdatedEvent.FieldChange(
                "isFeatured", String.valueOf(previousIsFeatured), String.valueOf(saved.isFeatured())
            ));
        }

        Set<UUID> newStylistIds = saved.getStylists().stream().map(Stylist::getId).collect(Collectors.toSet());
        if (!Objects.equals(previousStylistIds, newStylistIds)) {
            changes.add(new SalonServiceUpdatedEvent.FieldChange(
                "stylists", previousStylistIds.size() + " assigned", newStylistIds.size() + " assigned"
            ));
        }

        User actor = resolveActor(actorId);
        eventPublisher.publishEvent(new SalonServiceUpdatedEvent(saved, actor, changes));

        return serviceMapper.toAdminDto(saved, imagesOf(saved));
    }

    @Transactional
    public AdminServiceDto deleteService(UUID id, UUID actorId) {
        SalonService service = serviceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        List<MediaImage> images = mediaImageRepository
            .findByEntityTypeAndEntityIdOrderBySortOrderAsc(ItemType.SALON_SERVICE, id);
        AdminServiceDto dto = serviceMapper.toAdminDto(service, images);
        List<String> publicIds = images.stream().map(MediaImage::getPublicId).toList();

        mediaImageRepository.deleteByEntityTypeAndEntityId(ItemType.SALON_SERVICE, id);
        serviceRepository.deleteById(id);

        User actor = resolveActor(actorId);
        eventPublisher.publishEvent(new SalonServiceDeletedEvent(service, actor));

        // H-14: files go only after the DB delete commits, same as deleteServices. Before, they were
        // deleted first, so an FK failure (e.g. appointments still pointing at the service) rolled the
        // rows back but left them pointing at images that no longer existed.
        // Tradeoff: if the app dies between commit and the Cloudinary call, the files are orphaned
        // (costs storage, nobody sees it). That is the cheaper failure: the old order broke live pages.
        CloudinaryCleanup.scheduleCloudinaryDeletion(publicIds, cloudinaryService::delete);

        return dto;
    }

    // M-24: returns AdminServiceDto, the type the schema promises for deleteServices. The entities it used
    // to return have different property names (durationMinutes vs durationInMinutes, itemCatalog vs
    // category), so non-null fields resolved to null and the client saw an error for a delete that had
    // committed. DTOs are built before the delete, while the rows (and their lazy stylists) are still loaded.
    @Transactional
    public List<AdminServiceDto> deleteServices(List<UUID> ids, UUID actorId) {
        if (ids == null || ids.isEmpty()) {
            throw BusinessRuleException.invalid("No service ids were provided.");
        }

        List<SalonService> services = serviceRepository.findAllById(ids);
        List<UUID> serviceIds = services.stream().map(SalonService::getId).toList();

        List<MediaImage> images = mediaImageRepository
            .findByEntityTypeAndEntityIdInOrderBySortOrderAsc(ItemType.SALON_SERVICE, serviceIds);
        List<String> publicIds = images.stream().map(MediaImage::getPublicId).toList();

        Map<UUID, List<MediaImage>> imagesByService = images.stream()
            .collect(Collectors.groupingBy(MediaImage::getEntityId));
        List<AdminServiceDto> deleted = services.stream()
            .map(service -> serviceMapper.toAdminDto(service, imagesByService.getOrDefault(service.getId(), List.of())))
            .toList();

        mediaImageRepository.deleteByEntityTypeAndEntityIdIn(ItemType.SALON_SERVICE, serviceIds);
        serviceRepository.deleteAll(services);

        if (!services.isEmpty()) {
            User actor = resolveActor(actorId);
            eventPublisher.publishEvent(new SalonServicesBulkDeletedEvent(services, actor));
        }

        CloudinaryCleanup.scheduleCloudinaryDeletion(publicIds, cloudinaryService::delete);

        return deleted;
    }

    // ---------- Helpers ----------

    private Sort toSort(ServiceSort sort) {
        if (sort == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        return switch (sort) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case PRICE_LOW_TO_HIGH -> Sort.by(Sort.Direction.ASC, "price");
            case PRICE_HIGH_TO_LOW -> Sort.by(Sort.Direction.DESC, "price");
            case DURATION_LOW_TO_HIGH -> Sort.by(Sort.Direction.ASC, "durationMinutes");
            case DURATION_HIGH_TO_LOW -> Sort.by(Sort.Direction.DESC, "durationMinutes");
        };
    }

    private List<Stylist> resolveStylists(Set<UUID> stylistIds) {
        List<Stylist> stylists = stylistRepository.findAllById(stylistIds);

        if (stylists.size() != stylistIds.size()) {
            throw new ResourceNotFoundException("One or more stylists not found");
        }

        return stylists;
    }

    private User resolveActor(UUID actorId) {
        return actorId != null ? userRepository.findById(actorId).orElse(null) : null;
    }

    // Image lookup for single-service responses; list endpoints batch-load images instead.
    private List<MediaImage> imagesOf(SalonService service) {
        return mediaImageRepository.findByEntityTypeAndEntityIdOrderBySortOrderAsc(ItemType.SALON_SERVICE, service.getId());
    }
}
