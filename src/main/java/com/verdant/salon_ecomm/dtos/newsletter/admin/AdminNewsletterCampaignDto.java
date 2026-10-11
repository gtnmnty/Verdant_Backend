package com.verdant.salon_ecomm.dtos.newsletter.admin;

import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterCampaignStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminNewsletterCampaignDto(
    UUID id,
    String subject,
    String body,
    String ctaLabel,
    String ctaUrl,
    NewsletterCampaignStatus status,
    Integer recipientCount,      // null until sending has started
    int sentCount,
    int failedCount,
    String createdByName,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
