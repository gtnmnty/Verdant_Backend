package com.verdant.salon_ecomm.dtos.journal;

import com.verdant.salon_ecomm.models.enums.journal.JournalCategory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Public view - only ever built from PUBLISHED stories.
public record JournalStoryDto(
    UUID id,
    String clientName,          // display name as published, e.g. "Sophia M."
    String quote,
    JournalCategory category,
    String service,
    String stylist,
    JournalImageDto resultImage,
    JournalImageDto productImage,   // optional inset photo
    List<JournalProductDto> products,
    OffsetDateTime publishedAt
) {}
