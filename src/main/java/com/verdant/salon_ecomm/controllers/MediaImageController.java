package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.services.MediaImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/services/{serviceId}/images")
@RequiredArgsConstructor
public class MediaImageController {

    private final MediaImageService mediaImageService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<List<MediaImageDto>> addImages(
        @PathVariable UUID serviceId,
        @RequestParam("files") List<MultipartFile> files,
        @RequestParam(value = "isPrimary", required = false, defaultValue = "false") boolean isPrimary
    ) {
        List<MediaImageDto> results = mediaImageService.addImages(ItemType.SALON_SERVICE, serviceId, files, isPrimary);
        return ResponseEntity.status(HttpStatus.CREATED).body(results);
    }

    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<Void> removeImage(
        @PathVariable UUID serviceId,
        @PathVariable UUID imageId
    ) {
        mediaImageService.removeImage(ItemType.SALON_SERVICE, imageId, serviceId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{imageId}/primary")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<MediaImageDto> setImagePrimary(
        @PathVariable UUID serviceId,
        @PathVariable UUID imageId
    ) {
        MediaImageDto result = mediaImageService.setPrimary(ItemType.SALON_SERVICE, imageId, serviceId);
        return ResponseEntity.ok(result);
    }
}
