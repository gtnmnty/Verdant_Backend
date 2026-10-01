package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.GiftCardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GiftCardTransactionRepository extends JpaRepository<GiftCardTransaction, UUID> {

    List<GiftCardTransaction> findByGiftCardIdOrderByCreatedAtDesc(UUID giftCardId);

    @org.springframework.data.jpa.repository.Query("""
        SELECT t FROM GiftCardTransaction t
        WHERE t.giftCard.owner.id = :ownerId
        ORDER BY t.createdAt DESC
    """)
    List<GiftCardTransaction> findByOwnerIdOrderByCreatedAtDesc(@org.springframework.data.repository.query.Param("ownerId") UUID ownerId);
}