package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.dtos.product.events.ProductImageUpdatedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceImageUpdatedEvent;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.models.entities.MediaImage;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.repositories.SalonServiceRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.verdant.salon_ecomm.utils.CloudinaryCleanup;
import com.verdant.salon_ecomm.validation.ImageUploadValidator;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaImageService {

    private static final int MAX_IMAGES = 4;

    private final MediaImageRepository mediaImageRepository;
    private final CloudinaryService cloudinaryService;
    private final SalonServiceRepository salonServiceRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final TransactionTemplate transactionTemplate;

    // M-25: the type check moved to ImageUploadValidator, shared with avatars and the journal.
    private static final long MAX_FILE_SIZE_BYTES = ImageUploadValidator.DEFAULT_MAX_BYTES;

    // M-26: deliberately NOT @Transactional. Cloudinary uploads used to run inside the transaction, holding
    // one of the 10 pooled DB connections for every slow upload; a few at once could starve the whole app.
    // Same pattern as JournalService.createStory. Flow:
    //   1. cheap checks (entity exists, files are real images, image limit); each repository call is its
    //      own short read, no connection is held in between
    //   2. upload every file to Cloudinary, no DB connection held
    //   3. one short TransactionTemplate: re-check the limit, clear the old primary, insert the rows,
    //      publish the event (its listeners run after this commit)
    //   4. if step 2 or 3 fails, the files already uploaded are deleted, so Cloudinary keeps no orphans
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public List<MediaImageDto> addImages(
        ItemType entityType, UUID entityId,
        List<MultipartFile> files, Boolean isPrimary,
        UUID actorId
    ) {
        validateEntityExists(entityType, entityId);
        // M-25: shared validator (type from the file bytes, allow-list, size), used by every upload path.
        ImageUploadValidator.validateAll(files, MAX_FILE_SIZE_BYTES);
        requireRoomFor(entityType, entityId, files.size()); // fail before uploading anything

        List<CloudinaryService.CloudinaryUploadResult> uploads = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                uploads.add(cloudinaryService.upload(file));
            }

            boolean primary = Boolean.TRUE.equals(isPrimary);
            return transactionTemplate.execute(tx -> {
                // M-40: lock the product/service row first, so concurrent adds and setPrimary calls for the
                // same item run one after another. Without it, two adds could both pass the image limit and
                // two "primary" changes could each leave their own image primary.
                lockParent(entityType, entityId);
                // Re-checked inside the transaction: another upload may have added images meanwhile.
                long currentCount = requireRoomFor(entityType, entityId, uploads.size());
                if (primary) {
                    mediaImageRepository.clearPrimaryFlag(entityType, entityId);
                }

                List<MediaImageDto> results = new ArrayList<>();
                int sortOrder = (int) currentCount;
                for (CloudinaryService.CloudinaryUploadResult uploaded : uploads) {
                    MediaImage image = MediaImage.builder()
                        .entityType(entityType)
                        .entityId(entityId)
                        .url(uploaded.url())
                        .publicId(uploaded.publicId())
                        .isPrimary(primary && results.isEmpty()) // only the first file becomes primary
                        .sortOrder(sortOrder++)
                        .build();
                    results.add(toImageDTO(mediaImageRepository.save(image)));
                }

                if (primary) {
                    User actor = resolveActor(actorId);
                    if (entityType == ItemType.SALON_SERVICE) {
                        salonServiceRepository.findById(entityId).ifPresent(service ->
                            eventPublisher.publishEvent(new SalonServiceImageUpdatedEvent(service, actor)));
                    } else if (entityType == ItemType.PRODUCT) {
                        productRepository.findById(entityId).ifPresent(product ->
                            eventPublisher.publishEvent(new ProductImageUpdatedEvent(product, actor)));
                    }
                }
                return results;
            });
        } catch (RuntimeException ex) {
            for (CloudinaryService.CloudinaryUploadResult uploaded : uploads) {
                try {
                    cloudinaryService.delete(uploaded.publicId());
                } catch (RuntimeException cleanupEx) {
                    ex.addSuppressed(cleanupEx);
                }
            }
            throw ex;
        }
    }

    // Returns the current image count; throws if adding that many more would pass MAX_IMAGES.
    private long requireRoomFor(ItemType entityType, UUID entityId, int adding) {
        long currentCount = mediaImageRepository.countByEntityTypeAndEntityId(entityType, entityId);
        if (currentCount + adding > MAX_IMAGES) {
            throw BusinessRuleException.conflict("Maximum of " + MAX_IMAGES + " images allowed.");
        }
        return currentCount;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public boolean removeImage(ItemType itemType, UUID imageId, UUID serviceId) {
        MediaImage image = mediaImageRepository.findByIdAndEntityIdAndEntityType(
            imageId, serviceId, itemType
        ).orElseThrow(() -> new ResourceNotFoundException(
                "Image not found for " + itemType + " " + serviceId
            )
        );

        mediaImageRepository.deleteById(imageId);
        // H-14: delete the file after commit, not before the row. If the transaction rolled back, the
        // media_images row would survive and point at a file that's already gone.
        CloudinaryCleanup.scheduleCloudinaryDeletion(List.of(image.getPublicId()), cloudinaryService::delete);

        return true;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public MediaImageDto setPrimary(ItemType itemType, UUID imageId, UUID serviceId, UUID actorId) {
        // M-40: same per-item lock as addImages, taken before reading the images.
        lockParent(itemType, serviceId);
        MediaImage image = mediaImageRepository.findByIdAndEntityIdAndEntityType(
            imageId, serviceId, itemType
        ).orElseThrow(() -> new ResourceNotFoundException("Image not found"));

        // Excludes the selected image from the bulk clear so its own row is never
        // touched — avoids the dirty-checking no-op where setPrimary(true) is
        // skipped by Hibernate because the in-memory value already matched.
        mediaImageRepository.clearPrimaryFlagExcept(image.getEntityType(), image.getEntityId(), imageId);
        image.setPrimary(true);

        MediaImageDto dto = toImageDTO(mediaImageRepository.save(image));

        if (itemType == ItemType.SALON_SERVICE) {
            salonServiceRepository.findById(serviceId).ifPresent(service -> {
                User actor = resolveActor(actorId);
                eventPublisher.publishEvent(new SalonServiceImageUpdatedEvent(service, actor));
            });
        } else if (itemType == ItemType.PRODUCT) {
            productRepository.findById(serviceId).ifPresent(product -> {
                User actor = resolveActor(actorId);
                eventPublisher.publishEvent(new ProductImageUpdatedEvent(product, actor));
            });
        }

        return dto;
    }

    // M-40: SELECT ... FOR UPDATE on the product or service that owns the images. Held until the calling
    // transaction ends; it is what makes the image-limit check and the primary flag change safe to run
    // concurrently. The partial unique index from V14 is the database backstop for the primary flag.
    private void lockParent(ItemType entityType, UUID entityId) {
        boolean found = switch (entityType) {
            case SALON_SERVICE -> salonServiceRepository.findByIdForUpdate(entityId).isPresent();
            case PRODUCT -> productRepository.findByIdForUpdate(entityId).isPresent();
        };
        if (!found) {
            throw new ResourceNotFoundException(entityType + " not found: " + entityId);
        }
    }

    private User resolveActor(UUID actorId) {
        return actorId != null ? userRepository.findById(actorId).orElse(null) : null;
    }

    @Transactional(readOnly = true)
    public Map<UUID, MediaImageDto> getPrimaryImagesByEntityIds(ItemType entityType, List<UUID> entityIds) {
        List<MediaImage> primaryImages = mediaImageRepository
            .findByEntityTypeAndEntityIdInAndIsPrimaryTrue(entityType, entityIds);

        // M-40: the merge function keeps the first image if an item ever has two primaries (possible before
        // V14). Plain toMap threw on the duplicate key, which broke primaryImage for the whole page.
        return primaryImages.stream()
            .collect(Collectors.toMap(MediaImage::getEntityId, this::toImageDTO, (first, duplicate) -> first));
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
}