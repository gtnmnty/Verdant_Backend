package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.journal.CreateJournalStoryInput;
import com.verdant.salon_ecomm.dtos.journal.UpdateJournalStoryInput;
import com.verdant.salon_ecomm.dtos.journal.admin.AdminJournalStoryDto;
import com.verdant.salon_ecomm.models.enums.journal.JournalCategory;
import com.verdant.salon_ecomm.models.enums.journal.JournalStoryStatus;
import com.verdant.salon_ecomm.services.JournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

// Multipart entry point for staff story uploads. Spring GraphQL has no multipart-request support, so the
// Upload arguments on createJournalStory/updateJournalStory can't be sent from a browser; the admin form
// posts here instead. Reads, status changes and deletes still go through GraphQL.
@RestController
@RequestMapping("/api/journal")
@RequiredArgsConstructor
public class JournalController {

    private final JournalService journalService;

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<AdminJournalStoryDto> create(
        @RequestParam String clientName,
        @RequestParam String quote,
        @RequestParam JournalCategory category,
        @RequestParam String service,
        @RequestParam String stylist,
        @RequestParam(required = false) List<UUID> productIds,
        @RequestParam(required = false, defaultValue = "DRAFT") JournalStoryStatus status,
        @RequestParam MultipartFile resultImage,
        @RequestParam(required = false) MultipartFile productImage
    ) {
        var input = new CreateJournalStoryInput(
            clientName, quote, category, service, stylist, productIds, status);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(journalService.createStory(input, resultImage, productImage));
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MANAGER')")
    public ResponseEntity<AdminJournalStoryDto> update(
        @PathVariable UUID id,
        @RequestParam(required = false) String clientName,
        @RequestParam(required = false) String quote,
        @RequestParam(required = false) JournalCategory category,
        @RequestParam(required = false) String service,
        @RequestParam(required = false) String stylist,
        @RequestParam(required = false) List<UUID> productIds,
        @RequestParam(required = false) MultipartFile resultImage,
        @RequestParam(required = false) MultipartFile productImage
    ) {
        var input = new UpdateJournalStoryInput(clientName, quote, category, service, stylist, productIds);
        return ResponseEntity.ok(journalService.updateStory(id, input, resultImage, productImage));
    }
}
