package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.services.MediaImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class MediaImageResolver {

    private final MediaImageService mediaImageService;

    @MutationMapping
    public MediaImageDto addImage(
        @Argument ItemType entityType,
        @Argument UUID entityId,
        @Argument MultipartFile image,
        @Argument Boolean isPrimary
    ) {
        return mediaImageService.addImage(entityType, entityId, image, isPrimary);
    }

    @MutationMapping
    public boolean removeImage(@Argument UUID imageId) {
        return mediaImageService.removeImage(imageId);
    }

    @MutationMapping
    public MediaImageDto setImagePrimary(@Argument UUID imageId) {
        return mediaImageService.setPrimary(imageId);
    }
}