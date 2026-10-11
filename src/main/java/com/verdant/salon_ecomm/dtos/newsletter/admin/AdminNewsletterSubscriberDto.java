package com.verdant.salon_ecomm.dtos.newsletter.admin;

import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterSource;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminNewsletterSubscriberDto(
    UUID id,
    String email,
    NewsletterStatus status,
    NewsletterSource source,
    OffsetDateTime subscribedAt,
    OffsetDateTime unsubscribedAt,
    OffsetDateTime createdAt
) {}
