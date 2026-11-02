package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.services.MediaImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

// Product twin of MediaImageController (services). The admin product form used to send images as strings in
// createProduct/updateProduct, which the service ignored, so nothing reached media_images or Cloudinary.
// Files now go through MediaImageService.addImages, which uploads to Cloudinary and writes the rows.
@RestController
@RequestMapping("/api/products/{productId}/images")
@RequiredArgsConstructor
public class ProductImageController {

    private final MediaImageService mediaImageService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<List<MediaImageDto>> addImages(
        @PathVariable UUID productId,
        @RequestParam("files") List<MultipartFile> files,
        @RequestParam(value = "isPrimary", required = false, defaultValue = "false") boolean isPrimary,
        @AuthenticationPrincipal User principal
    ) {
        List<MediaImageDto> results = mediaImageService
            .addImages(ItemType.PRODUCT, productId, files, isPrimary, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(results);
    }

    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<Void> removeImage(
        @PathVariable UUID productId,
        @PathVariable UUID imageId
    ) {
        mediaImageService.removeImage(ItemType.PRODUCT, imageId, productId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{imageId}/primary")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<MediaImageDto> setImagePrimary(
        @PathVariable UUID productId,
        @PathVariable UUID imageId,
        @AuthenticationPrincipal User principal
    ) {
        MediaImageDto result = mediaImageService
            .setPrimary(ItemType.PRODUCT, imageId, productId, principal.getId());
        return ResponseEntity.ok(result);
    }
}
