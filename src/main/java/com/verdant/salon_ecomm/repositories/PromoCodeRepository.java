package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.promo_code.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {
    Optional<PromoCode> findByCode(String code);
}