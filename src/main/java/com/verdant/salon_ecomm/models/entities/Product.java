package com.verdant.salon_ecomm.models.entities;

import com.verdant.salon_ecomm.models.converters.StringListConverter;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name="category", nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    private ItemCatalog itemCatalog;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "image_urls", columnDefinition = "text[]")
    @Builder.Default
    private String[] images = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    @Builder.Default
    private String[] info = new String[0];

    @Column(length = 50)
    private String badge;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private Integer stockQuantity = 0;

    @Column(name = "review_count", nullable = false)
    @Builder.Default
    private Integer reviewCount = 0;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CollectionStatus status = CollectionStatus.ACTIVE;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(unique = true, length = 100)
    private String sku;

    @Column(name = "sale_price")
    private BigDecimal salePrice;

    @Column(name = "low_stock_threshold")
    @Builder.Default
    private int lowStockThreshold = 10;

    @Column(columnDefinition = "jsonb")
    @Convert(converter = StringListConverter.class)
    private List<String> tags;

    @Column(name = "is_featured")
    @Builder.Default
    private boolean isFeatured = false;

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

    // H-4: the one price a customer is actually charged. The sale price applies only when it is set,
    // positive and below the regular price, so a stale or mistyped sale price can never raise the
    // charge or make it free. Not a getter on purpose, so JPA and MapStruct don't treat it as a property.
    public BigDecimal effectivePrice() {
        if (salePrice != null && salePrice.signum() > 0 && price != null && salePrice.compareTo(price) < 0) {
            return salePrice;
        }
        return price;
    }
}
