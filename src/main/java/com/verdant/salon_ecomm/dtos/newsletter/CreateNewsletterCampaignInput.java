package com.verdant.salon_ecomm.dtos.newsletter;

public record CreateNewsletterCampaignInput(
    String subject,
    String body,
    String ctaLabel,
    String ctaUrl
) {}
