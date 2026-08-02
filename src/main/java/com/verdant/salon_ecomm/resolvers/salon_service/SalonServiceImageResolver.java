package com.verdant.salon_ecomm.resolvers.salon_service;

import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class SalonServiceImageResolver {
    private final MediaImageRepository mediaImageRepository;

    @BatchMapping(typeName = "SalonService")
    public Map<SalonService, List<MediaImage>> images(List<SalonService> services) {
        List<UUID> serviceIds = services.stream()
            .map(SalonService::getId)
            .toList();

        List<MediaImage> allImages = mediaImageRepository
            .findByEntityTypeAndEntityIdInOrderBySortOrderAsc(ItemType.SALON_SERVICE, serviceIds);

        Map<UUID, List<MediaImage>> imagesByServiceId = allImages.stream()
            .collect(Collectors.groupingBy(MediaImage::getEntityId));

        return services.stream()
            .collect(Collectors.toMap(
                service -> service,
                service -> imagesByServiceId.getOrDefault(service.getId(), List.of())
            ));
    }
}