package com.verdant.salon_ecomm.models.entities.newsletter;

import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterSource;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
    name = "newsletter_subscribers",
    indexes = @Index(
        name = "idx_newsletter_status_created",
        columnList = "status, created_at"
    )
)
public class NewsletterSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Always stored trimmed and lower-cased by the service, so the unique constraint really is per address.
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Builder.Default
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private NewsletterStatus status = NewsletterStatus.SUBSCRIBED;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private NewsletterSource source;

    // Random, unguessable. Goes in the unsubscribe link of every newsletter so no login is needed to opt out.
    @Column(name = "unsubscribe_token", nullable = false, unique = true, length = 64)
    private String unsubscribeToken;

    @Column(name = "subscribed_at")
    private OffsetDateTime subscribedAt;

    @Column(name = "unsubscribed_at")
    private OffsetDateTime unsubscribedAt;

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
