package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.models.enums.ItemType;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MediaImageRepository extends JpaRepository<MediaImage, UUID> {

    List<MediaImage> findByEntityTypeAndEntityIdOrderBySortOrderAsc(ItemType entityType, UUID entityId);

    List<MediaImage> findByEntityTypeAndEntityIdInOrderBySortOrderAsc(
        ItemType entityType, Collection<UUID> entityIds
    );

    long countByEntityTypeAndEntityId(ItemType entityType, UUID entityId);

    void deleteByEntityTypeAndEntityId(ItemType entityType, UUID entityId);

    @Modifying
    @Query("UPDATE MediaImage m SET m.isPrimary = false WHERE m.entityType = :entityType AND m.entityId = :entityId")
    void clearPrimaryFlag(@Param("entityType") ItemType entityType, @Param("entityId") UUID entityId);
}