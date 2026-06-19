package com.verdant.salon_ecomm.entities;


import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "media_images")
public class MediaImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType; // "PRODUCT" or "SERVICE"

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "public_id", nullable = false, length = 255)
    private String publicId;

    @Column(name = "is_primary")
    private boolean isPrimary = false;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
