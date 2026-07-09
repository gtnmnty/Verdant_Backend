package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.repositories.SalonServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaImageService {

    private static final int MAX_IMAGES = 4;

    private final MediaImageRepository mediaImageRepository;
    private final CloudinaryService cloudinaryService;
    private final SalonServiceRepository salonServiceRepository;
    private final ProductRepository productRepository;

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp"
    );

    @Transactional
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public List<MediaImageDto> addImages(ItemType entityType, UUID entityId, List<MultipartFile> files, Boolean isPrimary) {

        validateEntityExists(entityType, entityId);
        validateFiles(files);

        long currentCount = mediaImageRepository.countByEntityTypeAndEntityId(entityType, entityId);

        if (currentCount + files.size() > MAX_IMAGES) {
            throw new IllegalStateException("Maximum of " + MAX_IMAGES + " images allowed.");
        }

        boolean primary = Boolean.TRUE.equals(isPrimary);
        if (primary) {
            mediaImageRepository.clearPrimaryFlag(entityType, entityId);
        }

        List<MediaImageDto> results = new ArrayList<>();
        List<String> uploadedPublicIds = new ArrayList<>();
        int sortOrder = (int) currentCount;

        try {
            for (MultipartFile file : files) {
                CloudinaryService.CloudinaryUploadResult uploaded = cloudinaryService.upload(file);
                uploadedPublicIds.add(uploaded.publicId());

                boolean isThisPrimary = primary && results.isEmpty();

                MediaImage image = MediaImage.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .url(uploaded.url())
                    .publicId(uploaded.publicId())
                    .isPrimary(isThisPrimary)
                    .sortOrder(sortOrder++)
                    .build();

                results.add(toImageDTO(mediaImageRepository.save(image)));
            }
        } catch (RuntimeException ex) {
            for (String publicId : uploadedPublicIds) {
                try {
                    cloudinaryService.delete(publicId);
                } catch (RuntimeException cleanupEx) {
                    ex.addSuppressed(cleanupEx);
                }
            }
            throw ex;
        }

        return results;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public boolean removeImage(ItemType itemType, UUID imageId, UUID serviceId) {
        MediaImage image = mediaImageRepository.findByIdAndEntityIdAndEntityType(
            imageId, serviceId, itemType
        ).orElseThrow(() -> new ResourceNotFoundException(
                "Image not found for this service specification"
            )
        );

        cloudinaryService.delete(image.getPublicId());
        mediaImageRepository.deleteById(imageId);

        return true;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public MediaImageDto setPrimary(ItemType itemType, UUID imageId, UUID serviceId) {
        MediaImage image = mediaImageRepository.findByIdAndEntityIdAndEntityType(
            imageId, serviceId, itemType
        ).orElseThrow(() -> new ResourceNotFoundException("Image not found"));

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

    private void validateEntityExists(ItemType entityType, UUID entityId) {
        boolean exists = switch (entityType) {
            case SALON_SERVICE -> salonServiceRepository.existsById(entityId);
            case PRODUCT -> productRepository.existsById(entityId);
        };

        if (!exists) {
            throw new ResourceNotFoundException(entityType + " not found: " + entityId);
        }
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one file is required.");
        }

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("Uploaded file '" + file.getOriginalFilename() + "' is empty.");
            }
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new IllegalArgumentException(
                    "File '" + file.getOriginalFilename() + "' exceeds max size of " + (MAX_FILE_SIZE_BYTES / 1024 / 1024) + "MB."
                );
            }
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
                throw new IllegalArgumentException(
                    "File '" + file.getOriginalFilename() + "' has unsupported type: " + contentType
                );
            }
        }
    }
}