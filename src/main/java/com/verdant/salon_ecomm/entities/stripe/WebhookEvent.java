package com.verdant.salon_ecomm.entities.stripe;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
    name = "stripe_webhook_events",
    uniqueConstraints = @UniqueConstraint(name = "uq_webhook_stripe_event_id", columnNames = "stripe_event_id")
)
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "stripe_event_id", nullable = false, updatable = false, length = 100)
    private String stripeEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @PrePersist
    void onCreate() {
        if (this.processedAt == null) {
            this.processedAt = Instant.now();
        }
    }
}
