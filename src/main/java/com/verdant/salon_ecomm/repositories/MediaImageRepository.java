package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.models.enums.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaImageRepository extends JpaRepository<MediaImage, UUID> {
    List<MediaImage> findByEntityTypeAndEntityId(ItemType entityType, UUID entityId);
    Optional<MediaImage> findByEntityTypeAndEntityIdAndIsPrimaryTrue(ItemType entityType, UUID entityId);
    void deleteByEntityTypeAndEntityId(ItemType entityType, UUID entityId);
}