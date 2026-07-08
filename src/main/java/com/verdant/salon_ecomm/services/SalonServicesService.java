package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.dtos.service.*;
import com.verdant.salon_ecomm.dtos.stylists.StylistSummaryDto;
import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.models.enums.*;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import com.verdant.salon_ecomm.repositories.SalonServiceRepository;
import com.verdant.salon_ecomm.repositories.StylistRepository;
import com.verdant.salon_ecomm.specifications.ServiceSpec;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalonServicesService {

    private final SalonServiceRepository serviceRepository;
    private final MediaImageRepository mediaImageRepository;
    private final StylistRepository stylistRepository;

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

        return new ServicePage(
            result.getContent(),
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

        List<AdminServiceDto> services = result.getContent().stream()
            .map(this::toAdminDto)
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

    public AdminServiceDto toAdminDto(SalonService service){
        List<MediaImage> images = mediaImageRepository
            .findByEntityTypeAndEntityIdOrderBySortOrderAsc(
                ItemType.SALON_SERVICE, service.getId()
            );

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
    public AdminServiceDto createServiceInput(CreateServiceInput input) {
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
            .build();

        return toAdminDto(serviceRepository.save(service));
    }

    @Transactional
    public AdminServiceDto updateServiceInput(UpdateServiceInput input) {
        SalonService service =  serviceRepository.findById(input.id())
            .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

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

        return  toAdminDto(serviceRepository.save(service));
    }

    @Transactional
    public AdminServiceDto deleteService(UUID id) {
        SalonService service = serviceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        mediaImageRepository.deleteByEntityTypeAndEntityId(ItemType.SALON_SERVICE, id);
        serviceRepository.deleteById(id);
        return toAdminDto(service);
    }
}
