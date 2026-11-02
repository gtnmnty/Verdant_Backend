package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.services.StylistAvatarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/stylists/{stylistId}/avatar")
@RequiredArgsConstructor
public class StylistAvatarController {

    private final StylistAvatarService stylistAvatarService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, String>> updateAvatar(
        @PathVariable UUID stylistId,
        @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(Map.of("avatarUrl", stylistAvatarService.updateAvatar(stylistId, file)));
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<Void> removeAvatar(@PathVariable UUID stylistId) {
        stylistAvatarService.removeAvatar(stylistId);
        return ResponseEntity.noContent().build();
    }
}
