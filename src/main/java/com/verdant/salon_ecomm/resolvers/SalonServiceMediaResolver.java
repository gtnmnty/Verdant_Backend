package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import com.verdant.salon_ecomm.services.MediaImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class SalonServiceMediaResolver {

    private final MediaImageService mediaImageService;

    @BatchMapping(typeName = "SalonService")
    public Map<SalonService, MediaImageDto> primaryImage(List<SalonService> services) {
        List<UUID> serviceIds = services.stream().map(SalonService::getId).toList();

        Map<UUID, MediaImageDto> byServiceId =
            mediaImageService.getPrimaryImagesByEntityIds(ItemType.SALON_SERVICE, serviceIds);

        return services.stream()
            .collect(Collectors.toMap(s -> s, s -> byServiceId.get(s.getId())));
    }
}