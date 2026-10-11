package com.verdant.salon_ecomm.dtos.newsletter;

import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterDeliveryStatus;

import java.util.UUID;

// Result row of NewsletterDeliveryRepository.countByCampaignIds (JPQL constructor expression).
public record CampaignDeliveryCount(UUID campaignId, NewsletterDeliveryStatus status, Long count) {}
