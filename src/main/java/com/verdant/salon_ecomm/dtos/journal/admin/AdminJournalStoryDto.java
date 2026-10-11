package com.verdant.salon_ecomm.dtos.journal.admin;

import com.verdant.salon_ecomm.dtos.journal.JournalImageDto;
import com.verdant.salon_ecomm.dtos.journal.JournalProductDto;
import com.verdant.salon_ecomm.models.enums.journal.JournalCategory;
import com.verdant.salon_ecomm.models.enums.journal.JournalStoryStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminJournalStoryDto(
    UUID id,
    String clientName,
    String quote,
    JournalCategory category,
    String service,
    String stylist,
    JournalStoryStatus status,
    JournalImageDto resultImage,
    JournalImageDto productImage,
    List<JournalProductDto> products,
    OffsetDateTime publishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
