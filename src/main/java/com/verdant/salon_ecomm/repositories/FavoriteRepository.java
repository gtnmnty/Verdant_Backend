package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Favorite;
import com.verdant.salon_ecomm.models.enums.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {
    List<Favorite> findByUserId(UUID userId);

    Optional<Favorite> findByUserIdAndTargetIdAndTargetType(UUID userId, UUID targetId, ItemType targetType);

    void deleteByUserIdAndTargetIdAndTargetType(UUID userId, UUID targetId, ItemType targetType);
}