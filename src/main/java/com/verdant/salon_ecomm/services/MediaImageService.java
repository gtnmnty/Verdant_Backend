package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaImageService {

    private static final int MAX_IMAGES = 4;

    private final MediaImageRepository mediaImageRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MediaImageDto addImage(ItemType entityType, UUID entityId, MultipartFile file, Boolean isPrimary) {
        long currentCount = mediaImageRepository.countByEntityTypeAndEntityId(entityType, entityId);

        if (currentCount >= MAX_IMAGES) {
            throw new IllegalStateException("Maximum of " + MAX_IMAGES + " images allowed.");
        }

        CloudinaryService.CloudinaryUploadResult uploaded = cloudinaryService.upload(file);
        boolean primary = Boolean.TRUE.equals(isPrimary);

        if (primary) {
            mediaImageRepository.clearPrimaryFlag(entityType, entityId);
        }

        MediaImage image = MediaImage.builder()
            .entityType(entityType)
            .entityId(entityId)
            .url(uploaded.url())
            .publicId(uploaded.publicId())
            .isPrimary(primary)
            .sortOrder((int) currentCount)
            .build();

        return toImageDTO(mediaImageRepository.save(image));
    }

    @Transactional
    public boolean removeImage(UUID imageId) {
        MediaImage image = mediaImageRepository.findById(imageId)
            .orElseThrow(() -> new ResourceNotFoundException("Image not found"));

        cloudinaryService.delete(image.getPublicId());
        mediaImageRepository.deleteById(imageId);

        return true;
    }

    @Transactional
    public MediaImageDto setPrimary(UUID imageId) {
        MediaImage image = mediaImageRepository.findById(imageId)
            .orElseThrow(() -> new ResourceNotFoundException("Image not found"));

        mediaImageRepository.clearPrimaryFlag(image.getEntityType(), image.getEntityId());
        image.setPrimary(true);

        return toImageDTO(mediaImageRepository.save(image));
    }

    private MediaImageDto toImageDTO(MediaImage image) {
        return new MediaImageDto(
            image.getId().toString(),
            image.getUrl(),
            image.getPublicId(),
            image.isPrimary(),
            image.getSortOrder()
        );
    }
}