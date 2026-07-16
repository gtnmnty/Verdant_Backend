package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Review;
import com.verdant.salon_ecomm.models.enums.ItemType;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID>, JpaSpecificationExecutor<Review> {

        Optional<Review> findByUser_IdAndTargetTypeAndTargetId(UUID userId, ItemType targetType, UUID targetId);

        @Query("SELECT COALESCE(AVG(r.stars), 0) FROM Review r WHERE r.targetType = :targetType AND r.targetId = :targetId")
        BigDecimal findAverageRatingByTargetId(@Param("targetType") ItemType targetType, @Param("targetId") UUID targetId);

        @Query("SELECT COUNT(r) FROM Review r WHERE r.targetType = :targetType AND r.targetId = :targetId")
        int countByTargetId(@Param("targetType") ItemType targetType, @Param("targetId") UUID targetId);

        // Overrides JpaSpecificationExecutor's default findAll(spec, pageable) to
        // attach an EntityGraph — Review.user is @ManyToOne(LAZY), and this call
        // site (ReviewService.getReviews, customer-facing paginated list) always
        // needs the reviewer's name, so it's eager-fetched here specifically
        // rather than flipping the entity's default fetch type globally.
        @Override
        @EntityGraph(attributePaths = {"user"})
        Page<Review> findAll(Specification<Review> spec, @NonNull Pageable pageable);
}