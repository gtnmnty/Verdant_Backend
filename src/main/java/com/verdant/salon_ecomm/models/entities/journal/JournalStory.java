package com.verdant.salon_ecomm.models.entities.journal;

import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.StoredImage;
import com.verdant.salon_ecomm.models.enums.journal.JournalCategory;
import com.verdant.salon_ecomm.models.enums.journal.JournalStoryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
    name = "journal_stories",
    indexes = {
        @Index(name = "idx_journal_status_published", columnList = "status, published_at"),
        @Index(name = "idx_journal_category", columnList = "category")
    }
)
public class JournalStory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Name exactly as it should be published, e.g. "Sophia M." (get the client's consent first).
    @Column(name = "client_name", nullable = false, length = 100)
    private String clientName;

    @Column(nullable = false, columnDefinition = "text")
    private String quote;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private JournalCategory category;

    // Plain text on purpose: stories are historical
    // content and must keep reading correctly even if
    // the service is renamed or the stylist leaves.
    @Column(nullable = false, length = 200)
    private String service;

    @Column(nullable = false, length = 100)
    private String stylist;

    @Builder.Default
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private JournalStoryStatus status = JournalStoryStatus.DRAFT;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "url", column = @Column(name = "result_image_url", nullable = false, length = 500)),
        @AttributeOverride(name = "publicId", column = @Column(name = "result_image_public_id", nullable = false, length = 255)),
        @AttributeOverride(name = "width", column = @Column(name = "result_image_width", nullable = false)),
        @AttributeOverride(name = "height", column = @Column(name = "result_image_height", nullable = false))
    })
    private StoredImage resultImage;

    // Optional inset photo of the products used.
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "url", column = @Column(name = "product_image_url", length = 500)),
        @AttributeOverride(name = "publicId", column = @Column(name = "product_image_public_id", length = 255)),
        @AttributeOverride(name = "width", column = @Column(name = "product_image_width")),
        @AttributeOverride(name = "height", column = @Column(name = "product_image_height"))
    })
    private StoredImage productImage;

    /**
     * Used for WRITES (cascade + orphan removal). For reads, load links with their products in one query via
     * JournalStoryProductRepository.findAllWithProductByStoryIds - avoids lazy loading per story.
     */
    @Builder.Default
    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<JournalStoryProduct> products = new ArrayList<>();

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * Makes the story's products exactly {@code newProducts}, in that order.
     * Diffs instead of clear-and-re-add: Hibernate runs inserts before deletes, so re-inserting a
     * (story, product) pair that is still being deleted would violate the unique constraint.
     */
    public void replaceProducts(List<Product> newProducts) {
        Map<UUID, Product> wanted = new LinkedHashMap<>();
        for (Product p : newProducts) wanted.putIfAbsent(p.getId(), p);   // also drops duplicates

        Map<UUID, JournalStoryProduct> existing = new LinkedHashMap<>();
        for (JournalStoryProduct link : products) existing.put(link.getProduct().getId(), link);

        products.removeIf(link -> !wanted.containsKey(link.getProduct().getId()));

        int order = 0;
        for (Product p : wanted.values()) {
            JournalStoryProduct link = existing.get(p.getId());
            if (link != null) {
                link.setSortOrder(order);
            } else {
                products.add(JournalStoryProduct.builder().story(this).product(p).sortOrder(order).build());
            }
            order++;
        }
    }

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
