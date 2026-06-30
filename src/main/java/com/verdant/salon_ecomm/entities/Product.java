package com.verdant.salon_ecomm.entities;

import com.verdant.salon_ecomm.StringListConverter;
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
    private String[] images = {};

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] info = {};

    @Column(length = 50)
    private String badge;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity = 0;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount = 0;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CollectionStatus status = CollectionStatus.ACTIVE;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(unique = true, length = 100)
    private String sku;

    @Column(name = "sale_price")
    private BigDecimal salePrice;

    @Column(name = "low_stock_threshold")
    private int lowStockThreshold = 10;

    @Column(columnDefinition = "jsonb")
    @Convert(converter = StringListConverter.class)
    private List<String> tags;

    @Column(name = "is_featured")
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

}
