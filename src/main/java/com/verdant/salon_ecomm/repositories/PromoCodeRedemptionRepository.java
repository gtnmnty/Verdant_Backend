package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.promocode.PromoCodeRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PromoCodeRedemptionRepository extends JpaRepository<PromoCodeRedemption, UUID> {

    @Query("""
        SELECT COUNT(r) FROM PromoCodeRedemption r
        WHERE r.promoCode.id = :promoCodeId AND r.user.id = :userId AND r.releasedAt IS NULL
    """)
    long countByPromoCodeAndUser(@Param("promoCodeId") UUID promoCodeId, @Param("userId") UUID userId);

    // Order cancelled: its promo use goes back to the customer. Idempotent (only rows still counting are touched).
    @Modifying
    @Query("UPDATE PromoCodeRedemption r SET r.releasedAt = CURRENT_TIMESTAMP WHERE r.order.id = :orderId AND r.releasedAt IS NULL")
    int releaseForOrder(@Param("orderId") UUID orderId);

    // Staff re-opened a cancelled order: the use counts again.
    @Modifying
    @Query("UPDATE PromoCodeRedemption r SET r.releasedAt = NULL WHERE r.order.id = :orderId AND r.releasedAt IS NOT NULL")
    int reacquireForOrder(@Param("orderId") UUID orderId);

    List<PromoCodeRedemption> findByUserIdOrderByCreatedAtDesc(UUID userId);
}