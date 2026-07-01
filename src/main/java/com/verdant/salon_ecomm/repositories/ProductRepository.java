package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    List<Product> findByItemCatalog(ItemCatalog category);
    List<Product> findByIsFeaturedTrue();
    List<Product> findByStatus(CollectionStatus status);

    @Modifying
    @Query("UPDATE Product p SET p.reviewCount = :count, p.averageRating = :avg WHERE p.id = :id")
    int updateReviewAggregates(@Param("id") UUID id, @Param("count") int count, @Param("avg") BigDecimal avg);
}