package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.dtos.service.AdminServiceDto;
import com.verdant.salon_ecomm.dtos.service.SalonServiceDto;
import com.verdant.salon_ecomm.dtos.stylists.StylistSummaryDto;
import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SalonServiceMapper {

    private final MediaImageRepository mediaImageRepository;

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
            primaryImage != null ? toImageDto(primaryImage) : null
        );
    }

    public AdminServiceDto toAdminDto(SalonService service) {
        List<MediaImage> images = mediaImageRepository
            .findByEntityTypeAndEntityIdOrderBySortOrderAsc(
                ItemType.SALON_SERVICE, service.getId()
            );

        return toAdminDto(service, images);
    }

    public AdminServiceDto toAdminDto(SalonService service, List<MediaImage> images) {
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
            images.stream().map(this::toImageDto).toList(),
            service.getStylists().stream().map(this::toStylistSummaryDto).toList(),
            service.getCreatedAt(),
            service.getUpdatedAt()
        );
    }

    public StylistSummaryDto toStylistSummaryDto(Stylist stylist) {
        return new StylistSummaryDto(
            stylist.getId().toString(),
            stylist.getName(),
            stylist.getAvatarUrl(),
            stylist.getStatus()
        );
    }

    public MediaImageDto toImageDto(MediaImage image) {
        return new MediaImageDto(
            image.getId().toString(),
            image.getUrl(),
            image.getPublicId(),
            image.isPrimary(),
            image.getSortOrder()
        );
    }
}
