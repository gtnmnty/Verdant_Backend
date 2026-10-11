package com.verdant.salon_ecomm.models.entities.newsletter;

import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterDeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

//One row per (campaign, subscriber) attempt. This is what makes sending resumable: after a crash or an SMTP
//outage, only subscribers WITHOUT a row are still pending, so nobody gets the same newsletter twice.
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
    name = "newsletter_deliveries",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_newsletter_delivery",
        columnNames = {"campaign_id", "subscriber_id"}
    )
)
public class NewsletterDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private NewsletterCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private NewsletterSubscriber subscriber;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private NewsletterDeliveryStatus status;

    @Column(length = 500)
    private String error;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
