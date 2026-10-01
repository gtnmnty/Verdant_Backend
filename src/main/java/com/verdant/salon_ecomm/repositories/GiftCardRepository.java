package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.GiftCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GiftCardRepository extends JpaRepository<GiftCard, UUID> {

    Optional<GiftCard> findByCode(String code);

    Optional<GiftCard> findByStripePaymentIntentId(String stripePaymentIntentId);

    List<GiftCard> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    @Query("""
        SELECT COALESCE(SUM(g.balance), 0) FROM GiftCard g
        WHERE g.owner.id = :ownerId AND g.status = 'REDEEMED' AND g.balance > 0
    """)
    BigDecimal getRedeemableBalance(@Param("ownerId") UUID ownerId);

    @Query("""
        SELECT g FROM GiftCard g
        WHERE g.owner.id = :ownerId AND g.status = 'REDEEMED' AND g.balance > 0
        ORDER BY g.expiresAt ASC NULLS LAST, g.createdAt ASC
    """)
    List<GiftCard> findSpendableCardsForUpdate(@Param("ownerId") UUID ownerId);

    @Query("""
        SELECT g FROM GiftCard g
        WHERE g.status = 'ACTIVE' AND g.expiresAt IS NOT NULL AND g.expiresAt < CURRENT_TIMESTAMP
    """)
    List<GiftCard> findExpiredActiveCards();
}