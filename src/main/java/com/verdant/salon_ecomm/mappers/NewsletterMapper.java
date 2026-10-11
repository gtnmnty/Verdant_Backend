package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.newsletter.admin.AdminNewsletterCampaignDto;
import com.verdant.salon_ecomm.dtos.newsletter.admin.AdminNewsletterSubscriberDto;
import com.verdant.salon_ecomm.models.entities.newsletter.NewsletterCampaign;
import com.verdant.salon_ecomm.models.entities.newsletter.NewsletterSubscriber;
import org.springframework.stereotype.Component;

@Component
public class NewsletterMapper {

    // sent/failed are computed from the delivery rows by the service.
    // Call inside a transaction (createdBy is lazy).
    public AdminNewsletterCampaignDto toCampaignDto(NewsletterCampaign c, int sentCount, int failedCount) {
        return new AdminNewsletterCampaignDto(
            c.getId(),
            c.getSubject(),
            c.getBody(),
            c.getCtaLabel(),
            c.getCtaUrl(),
            c.getStatus(),
            c.getRecipientCount(),
            sentCount,
            failedCount,
            c.getCreatedBy() != null ? c.getCreatedBy().getFullName() : null,
            c.getStartedAt(),
            c.getCompletedAt(),
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }

    // The unsubscribe token is intentionally NOT exposed - it is a secret that only goes in emails.
    public AdminNewsletterSubscriberDto toAdminDto(NewsletterSubscriber s) {
        return new AdminNewsletterSubscriberDto(
            s.getId(),
            s.getEmail(),
            s.getStatus(),
            s.getSource(),
            s.getSubscribedAt(),
            s.getUnsubscribedAt(),
            s.getCreatedAt()
        );
    }
}
