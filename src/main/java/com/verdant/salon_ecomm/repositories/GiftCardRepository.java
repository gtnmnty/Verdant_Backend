package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.giftcards.GiftCard;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.giftcards.GiftCardStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

public interface GiftCardRepository extends JpaRepository<GiftCard, UUID> {

    boolean existsByCode(String code);

    // Row-locked read so two concurrent redeems of the same code can't both succeed.
    @Lock(PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GiftCard g WHERE g.code = :code")
    Optional<GiftCard> findByCodeForUpdate(@Param("code") String code);

    @Lock(PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GiftCard g WHERE g.id = :id")
    Optional<GiftCard> findByIdForUpdate(@Param("id") UUID id);

    // purchaser is fetched eagerly: events are consumed AFTER_COMMIT, when a lazy proxy
    // can no longer be initialized.
    @Query("SELECT g FROM GiftCard g LEFT JOIN FETCH g.purchaser WHERE g.stripePaymentIntentId = :paymentIntentId")
    Optional<GiftCard> findByStripePaymentIntentId(@Param("paymentIntentId") String paymentIntentId);

    List<GiftCard> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    List<GiftCard> findByPurchaserIdOrderByCreatedAtDesc(UUID purchaserId);

    // ── spendable = redeemed + has balance + not past expiry ─────

    @Query("""
            SELECT COALESCE(SUM(g.balance), 0) FROM GiftCard g
            WHERE g.owner.id = :ownerId AND g.status = :status AND g.balance > 0
              AND (g.expiresAt IS NULL OR g.expiresAt > :now)
        """)
    BigDecimal sumSpendableBalance(
        @Param("ownerId") UUID ownerId,
        @Param("status") GiftCardStatus status,
        @Param("now") OffsetDateTime now
    );

    @Lock(PESSIMISTIC_WRITE)
    @Query("""
            SELECT g FROM GiftCard g
            WHERE g.owner.id = :ownerId AND g.status = :status AND g.balance > 0
              AND (g.expiresAt IS NULL OR g.expiresAt > :now)
            ORDER BY g.expiresAt ASC NULLS LAST, g.createdAt ASC
        """)
    List<GiftCard> lockSpendableCards(
        @Param("ownerId") UUID ownerId,
        @Param("status") GiftCardStatus status,
        @Param("now") OffsetDateTime now
    );

    default BigDecimal getRedeemableBalance(UUID ownerId) {
        return sumSpendableBalance(ownerId, GiftCardStatus.REDEEMED, OffsetDateTime.now());
    }

    default List<GiftCard> findSpendableCardsForUpdate(UUID ownerId) {
        return lockSpendableCards(ownerId, GiftCardStatus.REDEEMED, OffsetDateTime.now());
    }

    // ── sweeps ───────────────────────────────────────────────────

    // owner fetched eagerly for the AFTER_COMMIT listeners (name/id lookups).
    @Query("""
            SELECT g FROM GiftCard g LEFT JOIN FETCH g.owner
            WHERE g.status IN :statuses AND g.expiresAt IS NOT NULL AND g.expiresAt < :now AND g.balance > 0
        """)
    List<GiftCard> findExpirable(
        @Param("statuses") Collection<GiftCardStatus> statuses,
        @Param("now") OffsetDateTime now, Pageable pageable);

    @Query("""
            SELECT g FROM GiftCard g
            WHERE g.status = :status AND g.paymentStatus IN :paymentStatuses AND g.createdAt < :cutoff
        """)
    List<GiftCard> findAbandonedPurchases(
        @Param("status") GiftCardStatus status,
        @Param("paymentStatuses") Collection<PaymentStatus> paymentStatuses,
        @Param("cutoff") OffsetDateTime cutoff, Pageable pageable
    );
}