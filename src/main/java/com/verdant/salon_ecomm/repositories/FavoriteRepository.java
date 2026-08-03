package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Favorite;
import com.verdant.salon_ecomm.models.enums.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {
    Optional<Favorite> findByUserIdAndTargetIdAndTargetType(UUID userId, UUID targetId, ItemType targetType);
    List<Favorite> findByUserIdAndTargetIdInAndTargetType(UUID userId, Collection<UUID> targetIds, ItemType targetType);
    List<Favorite> findByUserIdAndTargetType(UUID userId, ItemType itemType);

    @Modifying
    void deleteByUserId(UUID userId);
}