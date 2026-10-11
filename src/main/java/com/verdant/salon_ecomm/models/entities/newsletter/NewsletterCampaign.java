package com.verdant.salon_ecomm.models.entities.newsletter;

import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterCampaignStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

// One newsletter an admin writes and sends to every current subscriber.
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
    name = "newsletter_campaigns",
    indexes = @Index(name = "idx_newsletter_campaign_created", columnList = "created_at")
)
public class NewsletterCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String subject;

    // Plain text. Blank lines separate paragraphs; the email template escapes it, so no HTML is accepted.
    @Column(nullable = false, columnDefinition = "text")
    private String body;

    // Optional button under the text. Both fields are set or both are null.
    @Column(name = "cta_label", length = 60)
    private String ctaLabel;

    @Column(name = "cta_url", length = 500)
    private String ctaUrl;

    @Builder.Default
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private NewsletterCampaignStatus status = NewsletterCampaignStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    // Subscribers counted when sending started. Sent/failed numbers are computed from the delivery rows.
    @Column(name = "recipient_count")
    private Integer recipientCount;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
