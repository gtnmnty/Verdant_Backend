package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Review;
import com.verdant.salon_ecomm.models.enums.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID>, JpaSpecificationExecutor<Review> {

        Optional<Review> findByUser_IdAndTargetTypeAndTargetId(UUID userId, ItemType targetType, UUID targetId);

        @Query("SELECT COALESCE(AVG(r.stars), 0) FROM Review r WHERE r.targetId = :targetId")
        BigDecimal findAverageRatingByTargetId(@Param("targetId") UUID targetId);

        @Query("SELECT COUNT(r) FROM Review r WHERE r.targetId = :targetId")
        int countByTargetId(@Param("targetId") UUID targetId);
}