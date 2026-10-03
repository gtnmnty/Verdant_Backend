package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.GiftCardTransaction;
import com.verdant.salon_ecomm.models.enums.giftcards.GiftCardTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GiftCardTransactionRepository extends JpaRepository<GiftCardTransaction, UUID> {

    List<GiftCardTransaction> findByGiftCardIdOrderByCreatedAtDesc(UUID giftCardId);

    @Query("""
        SELECT t FROM GiftCardTransaction t
        WHERE t.giftCard.owner.id = :ownerId
        ORDER BY t.createdAt DESC
    """)
    List<GiftCardTransaction> findByOwnerIdOrderByCreatedAtDesc(@Param("ownerId") UUID ownerId);

    List<GiftCardTransaction> findByOrderIdAndType(UUID orderId, GiftCardTransactionType type);

    boolean existsByOrderIdAndType(UUID orderId, GiftCardTransactionType type);

    // Keeps the ledger rows but unlinks them so orders can be hard-deleted without an FK violation.
    @Modifying(flushAutomatically = true)
    @Query("UPDATE GiftCardTransaction t SET t.order = null WHERE t.order.id IN :orderIds")
    void detachOrders(@Param("orderIds") Collection<UUID> orderIds);
}