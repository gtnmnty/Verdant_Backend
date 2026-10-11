package com.verdant.salon_ecomm.dtos.journal;

import com.verdant.salon_ecomm.models.enums.journal.JournalCategory;

import java.util.List;
import java.util.UUID;


// Every field optional: null = leave unchanged.
// productIds: null = unchanged, a list (even empty) replaces the whole set.
// Status is changed through setJournalStoryStatus, not here.
public record UpdateJournalStoryInput(
    String clientName,
    String quote,
    JournalCategory category,
    String service,
    String stylist,
    List<UUID> productIds
) {}
