package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.promo_code.PromoCodeRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PromoCodeRedemptionRepository extends JpaRepository<PromoCodeRedemption, UUID> {

    @Query("""
        SELECT COUNT(r) FROM PromoCodeRedemption r
        WHERE r.promoCode.id = :promoCodeId AND r.user.id = :userId
    """)
    long countByPromoCodeAndUser(@Param("promoCodeId") UUID promoCodeId, @Param("userId") UUID userId);

    List<PromoCodeRedemption> findByUserIdOrderByCreatedAtDesc(UUID userId);
}