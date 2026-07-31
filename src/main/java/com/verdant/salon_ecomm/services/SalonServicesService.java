package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.dtos.service.*;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceCreatedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceDeletedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceUpdatedEvent;
import com.verdant.salon_ecomm.dtos.stylists.StylistSummaryDto;
import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.models.enums.*;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import com.verdant.salon_ecomm.repositories.SalonServiceRepository;
import com.verdant.salon_ecomm.repositories.StylistRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.specifications.ServiceSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ApplicationEventPublisher eventPublisher;

    public ServicePage getSalonServices(
        String category, String search, ServiceSort sort,
        int page, int pageSize
    ){
        int normalizePage = Math.max(page - 1, 0) + 1;
        int normalizePageSize = Math.max(pageSize, 1);

        Pageable pageable = PageRequest.of(normalizePage - 1, normalizePageSize, toSort(sort));

        Page<SalonService> result = serviceRepository.findAll(
            ServiceSpec.filterSalonService(category, search, CollectionStatus.ACTIVE),
            pageable
        );

        List<SalonService> services = result.getContent();
        if (services.isEmpty()) {
            return new ServicePage(
                List.of(),
                normalizePage,
                normalizePageSize,
                (int) result.getTotalElements(),
                result.getTotalPages()
            );
        }

        List<UUID> serviceIds = services.stream().map(SalonService::getId).toList();

        List<MediaImage> primaryImages = mediaImageRepository
            .findByEntityTypeAndEntityIdInAndIsPrimaryTrue(ItemType.SALON_SERVICE, serviceIds);

        Map<UUID, MediaImage> primaryImageMap = primaryImages.stream()
            .collect(Collectors.toMap(MediaImage::getEntityId, img -> img));

        List<SalonServiceDto> items = services.stream()
            .map(service -> toDto(service, primaryImageMap.get(service.getId())))
            .toList();

        return new ServicePage(
            items,
            normalizePage,
            normalizePageSize,
            (int) result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public SalonService getServiceDetail(UUID id){
        SalonService service = serviceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        if(service.getStatus() != CollectionStatus.ACTIVE){
            throw new ResourceNotFoundException("Service not found");
        }

        return service;
    }

    public AdminServicePage getAdminServices(
        String category, String search, ServiceSort sort,
        CollectionStatus status,
        int page, int pageSize
    ){
        int normalizePage = Math.max(page - 1, 0) + 1;
        int normalizePageSize = Math.max(pageSize, 1);

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
            .map(service -> toAdminDto(
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

    public AdminServiceDto getAdminServiceDto(UUID id){
        SalonService service = serviceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        return toAdminDto(service);
    }

    public SalonServiceDto toDto(SalonService service, MediaImage primaryImage) {
        return new SalonServiceDto(
            service.getId(),
            service.getName(),
            service.getSubName(),
            service.getItemCatalog(),
            service.getPrice(),
            service.getDurationMinutes(),
            service.getDescription(),
            service.getBadge(),
            primaryImage != null ? toImageDTO(primaryImage) : null
        );
    }

    public AdminServiceDto toAdminDto(SalonService service){
        List<MediaImage> images = mediaImageRepository
            .findByEntityTypeAndEntityIdOrderBySortOrderAsc(
                ItemType.SALON_SERVICE, service.getId()
            );

        return toAdminDto(service, images);
    }

    public AdminServiceDto toAdminDto(SalonService service, List<MediaImage> images){
        return new AdminServiceDto(
            service.getId().toString(),
            service.getName(),
            service.getSubName(),
            service.getItemCatalog().name(),
            service.getPrice(),
            service.getDurationMinutes(),
            service.getStatus(),
            service.getDescription(),
            service.getBadge(),
            service.getTags(),
            service.getInfo(),
            service.getReviewCount(),
            service.getAverageRating(),
            service.getIsHomeService(),
            service.isFeatured(),
            images.stream().map(this::toImageDTO).toList(),
            service.getStylists().stream().map(this::toStylistSummaryDto).toList(),
            service.getCreatedAt(),
            service.getUpdatedAt()
        );
    }

    private MediaImageDto toImageDTO(MediaImage image){
        return new MediaImageDto(
            image.getId().toString(),
            image.getUrl(),
            image.getPublicId(),
            image.isPrimary(),
            image.getSortOrder()
        );
    }

    private Sort toSort(ServiceSort sort){
        if (sort == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        return switch (sort) {
            case NEWEST ->  Sort.by(Sort.Direction.DESC, "createdAt");
            case OLDEST ->  Sort.by(Sort.Direction.ASC, "createdAt");
            case PRICE_LOW_TO_HIGH ->  Sort.by(Sort.Direction.ASC, "price");
            case PRICE_HIGH_TO_LOW ->   Sort.by(Sort.Direction.DESC, "price");
            case DURATION_LOW_TO_HIGH ->  Sort.by(Sort.Direction.ASC, "durationMinutes");
            case DURATION_HIGH_TO_LOW ->  Sort.by(Sort.Direction.DESC, "durationMinutes");
        };
    }

    private StylistSummaryDto toStylistSummaryDto(Stylist stylist) {
        return new StylistSummaryDto(
            stylist.getId().toString(),
            stylist.getName(),
            stylist.getAvatarUrl(),
            stylist.getStatus()
        );
    }

    private List<Stylist> resolveStylists(Set<UUID> stylistIds) {
        List<Stylist> stylists = stylistRepository.findAllById(stylistIds);

        if (stylists.size() != stylistIds.size()) {
            throw new ResourceNotFoundException("One or more stylists not found");
        }

        return stylists;
    }

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

        return toAdminDto(saved);
    }

    @Transactional
    public AdminServiceDto updateServiceInput(UpdateServiceInput input, UUID actorId) {
        SalonService service =  serviceRepository.findById(input.id())
            .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        String previousName = service.getName();
        String previousSubName = service.getSubName();
        ItemCatalog previousCatalog = service.getItemCatalog();
        BigDecimal previousPrice = service.getPrice();
        Integer previousDuration = service.getDurationMinutes();
        CollectionStatus previousStatus = service.getStatus();
        Boolean previousIsHomeService = service.getIsHomeService();
        Boolean previousIsFeatured = service.isFeatured();
        String previousDescription = service.getDescription();
        String previousInfo = String.valueOf(service.getInfo());
        var previousTags = service.getTags();
        String previousBadge = service.getBadge();
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
        if (!Objects.equals(previousDescription, saved.getDescription())) {
            changes.add(new SalonServiceUpdatedEvent.FieldChange(
                "description", previousDescription, saved.getDescription())
            );
        }
        if (!Objects.equals(previousInfo, saved.getInfo())) {
            changes.add(new SalonServiceUpdatedEvent.FieldChange(
                "info", String.valueOf(previousInfo), String.valueOf(saved.getInfo()))
            );
        }
        if (!Objects.equals(previousTags, saved.getTags())) {
            changes.add(new SalonServiceUpdatedEvent.FieldChange(
                "tags", String.valueOf(previousTags), String.valueOf(saved.getTags()))
            );
        }
        if (!Objects.equals(previousBadge, saved.getBadge())) {
            changes.add(new SalonServiceUpdatedEvent.FieldChange(
                "badge", previousBadge, saved.getBadge())
            );
        }

        Set<UUID> newStylistIds = saved.getStylists().stream().map(Stylist::getId).collect(Collectors.toSet());
        if (!Objects.equals(previousStylistIds, newStylistIds)) {
            changes.add(new SalonServiceUpdatedEvent.FieldChange(
                "stylists", previousStylistIds.size() + " assigned",
                newStylistIds.size() + " assigned"
            ));
        }

        User actor = resolveActor(actorId);
        eventPublisher.publishEvent(new SalonServiceUpdatedEvent(saved, actor, changes));

        return toAdminDto(saved);
    }

    @Transactional
    public AdminServiceDto deleteService(UUID id, UUID actorId) {
        SalonService service = serviceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        List<MediaImage> images = mediaImageRepository.findByEntityTypeAndEntityId(ItemType.SALON_SERVICE, id);

        for (MediaImage image : images) {
            cloudinaryService.delete(image.getPublicId());
        }

        AdminServiceDto dto = toAdminDto(service, images);

        mediaImageRepository.deleteByEntityTypeAndEntityId(ItemType.SALON_SERVICE, id);
        serviceRepository.deleteById(id);

        User actor = resolveActor(actorId);
        eventPublisher.publishEvent(new SalonServiceDeletedEvent(service, actor));

        return dto;
    }

    private User resolveActor(UUID actorId) {
        return actorId != null ? userRepository.findById(actorId).orElse(null) : null;
    }
}