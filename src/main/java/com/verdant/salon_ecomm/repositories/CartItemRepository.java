package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.CartItem;
import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID>, JpaSpecificationExecutor<CartItem> {

    List<CartItem> findByUser_IdOrderByAddedAtDesc(UUID userId);

    List<CartItem> findByIdInAndUser_Id(List<UUID> ids, UUID userId);

    Optional<CartItem> findByIdAndUser_Id(UUID id, UUID userId);

    // Used to merge quantities when the same product + delivery option is added again
    Optional<CartItem> findByUser_IdAndProduct_IdAndDeliveryOption(UUID userId, UUID productId, DeliveryOption deliveryOption);

    long countByUser_Id(UUID userId);

    void deleteByIdInAndUser_Id(List<UUID> ids, UUID userId);
}