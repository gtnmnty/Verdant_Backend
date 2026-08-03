package com.verdant.salon_ecomm.resolvers.salon_service;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.mappers.SalonServiceMapper;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ServicePrimaryImageResolver {

    private final MediaImageRepository mediaImageRepository;
    private final SalonServiceMapper serviceMapper;

    @BatchMapping(typeName = "SalonService", field = "primaryImage")
    public Map<SalonService, MediaImageDto> primaryImage(List<SalonService> services) {
        List<UUID> serviceIds = services.stream().map(SalonService::getId).toList();

        Map<UUID, MediaImage> primaryImageMap = mediaImageRepository
            .findByEntityTypeAndEntityIdInAndIsPrimaryTrue(ItemType.SALON_SERVICE, serviceIds)
            .stream()
            .collect(Collectors.toMap(MediaImage::getEntityId, img -> img));

        Map<SalonService, MediaImageDto> result = new HashMap<>();
        for (SalonService service : services) {
            MediaImage img = primaryImageMap.get(service.getId());
            result.put(service, img != null ? serviceMapper.toImageDto(img) : null);
        }
        return result;
    }
}
