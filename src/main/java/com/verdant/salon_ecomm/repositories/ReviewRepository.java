package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Review;
import com.verdant.salon_ecomm.models.enums.ItemType;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
        List<Review> findByTargetIdAndTargetType(UUID targetId, ItemType targetType);
        List<Review> findByUserId(UUID userId);
        int countByTargetIdAndTargetType(UUID targetId, ItemType targetType);
}