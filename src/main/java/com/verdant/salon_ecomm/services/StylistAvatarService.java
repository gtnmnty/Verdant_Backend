package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.models.entities.Stylist;
import com.verdant.salon_ecomm.repositories.StylistRepository;
import com.verdant.salon_ecomm.validation.ImageUploadValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.UUID;

// Stylist twin of UserService.updateAvatar. Before this, the admin form saved the picked image as a
// data: URL straight into stylists.avatar_url and nothing reached Cloudinary. Not part of StylistsService
// because that class is @Transactional and the upload must not hold a DB connection.
// Flow: validate -> stylist exists? -> upload -> short transaction swaps the avatar -> on failure the new
// upload is deleted -> on success the old file is deleted in the background.
@Service
@RequiredArgsConstructor
public class StylistAvatarService {

    private final StylistRepository stylistRepository;
    private final CloudinaryService cloudinaryService;
    private final CloudinaryAsyncCleanup cloudinaryAsyncCleanup;
    private final TransactionTemplate transactionTemplate;

    public String updateAvatar(UUID stylistId, MultipartFile file) {
        ImageUploadValidator.validate(file, "Avatar", ImageUploadValidator.DEFAULT_MAX_BYTES);
        if (!stylistRepository.existsById(stylistId)) {
            throw new ResourceNotFoundException("Stylist not found");
        }

        CloudinaryService.CloudinaryUploadResult uploaded = cloudinaryService.upload(file);

        String previousPublicId;
        try {
            previousPublicId = transactionTemplate.execute(tx -> {
                Stylist stylist = findOrThrow(stylistId);
                String previous = stylist.getAvatarPublicId();
                stylist.setAvatarUrl(uploaded.url());
                stylist.setAvatarPublicId(uploaded.publicId());
                stylist.setUpdatedAt(OffsetDateTime.now());
                stylistRepository.save(stylist);
                return previous;
            });
        } catch (RuntimeException e) {
            // Nothing references the new file: the swap never committed.
            try {
                cloudinaryService.delete(uploaded.publicId());
            } catch (RuntimeException cleanupEx) {
                e.addSuppressed(cleanupEx);
            }
            throw e;
        }

        if (previousPublicId != null && !previousPublicId.equals(uploaded.publicId())) {
            cloudinaryAsyncCleanup.deleteWithRetry(previousPublicId);
        }
        return uploaded.url();
    }

    public void removeAvatar(UUID stylistId) {
        String previousPublicId = transactionTemplate.execute(tx -> {
            Stylist stylist = findOrThrow(stylistId);
            String previous = stylist.getAvatarPublicId();
            stylist.setAvatarUrl(null);
            stylist.setAvatarPublicId(null);
            stylist.setUpdatedAt(OffsetDateTime.now());
            stylistRepository.save(stylist);
            return previous;
        });
        if (previousPublicId != null) {
            cloudinaryAsyncCleanup.deleteWithRetry(previousPublicId);
        }
    }

    private Stylist findOrThrow(UUID id) {
        return stylistRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Stylist not found"));
    }
}
