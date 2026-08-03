package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.CartItem;
import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.graphql.data.method.annotation.Argument;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID>, JpaSpecificationExecutor<CartItem> {

    List<CartItem> findByUser_IdOrderByAddedAtDesc(UUID userId);

    Optional<CartItem> findByIdAndUser_Id(UUID id, UUID userId);

    // Used to merge quantities when the same product + delivery option is added again
    Optional<CartItem> findByUser_IdAndProduct_IdAndDeliveryOption(UUID userId, UUID productId, DeliveryOption deliveryOption);

    @Override
    @EntityGraph(attributePaths = "product")
    List<CartItem> findAll(Specification<CartItem> spec);

    @Modifying
    void deleteByUserId(UUID userId);
}