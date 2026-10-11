package com.verdant.salon_ecomm.models.entities.journal;

import com.verdant.salon_ecomm.models.entities.Product;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

// Ordered link between a journal story and a catalog product.
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
    name = "journal_story_products",
    uniqueConstraints = @UniqueConstraint(name = "uq_journal_story_product", columnNames = {"story_id", "product_id"}),
    indexes = @Index(name = "idx_journal_story_products_product", columnList = "product_id")
)
public class JournalStoryProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id", nullable = false)
    private JournalStory story;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
