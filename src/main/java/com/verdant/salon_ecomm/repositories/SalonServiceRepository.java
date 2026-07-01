package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface SalonServiceRepository extends JpaRepository<SalonService, UUID> {
    List<SalonService> findByItemCatalog(ItemCatalog itemCatalog);
    List<SalonService> findByIsFeaturedTrue();

    @Modifying
    @Query("UPDATE SalonService s SET s.reviewCount = :count, s.averageRating = :avg WHERE s.id = :id")
    int updateReviewAggregates(@Param("id") UUID id, @Param("count") int count, @Param("avg") BigDecimal avg);
}