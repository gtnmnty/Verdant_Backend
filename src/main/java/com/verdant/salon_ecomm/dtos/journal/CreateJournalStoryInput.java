package com.verdant.salon_ecomm.dtos.journal;

import com.verdant.salon_ecomm.models.enums.journal.JournalCategory;
import com.verdant.salon_ecomm.models.enums.journal.JournalStoryStatus;

import java.util.List;
import java.util.UUID;

// The images are not part of this record -
// they arrive as separate Upload arguments on the mutation.
public record CreateJournalStoryInput(
    String clientName,
    String quote,
    JournalCategory category,
    String service,
    String stylist,
    List<UUID> productIds,          // display order = list order
    JournalStoryStatus status       // defaults to DRAFT
) {}
