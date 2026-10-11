package com.verdant.salon_ecomm.dtos.newsletter;

// null = unchanged. For the button, an empty string clears it (clear BOTH ctaLabel and ctaUrl). */
public record UpdateNewsletterCampaignInput(
    String subject,
    String body,
    String ctaLabel,
    String ctaUrl
) {}
