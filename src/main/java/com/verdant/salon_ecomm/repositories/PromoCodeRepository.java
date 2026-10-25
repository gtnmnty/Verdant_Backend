package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.promocode.PromoCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {
    Optional<PromoCode> findByCode(String code);

    // M-11: SELECT ... FOR UPDATE. Held until the order transaction commits, so concurrent checkouts
    // with the same code run their "uses so far" count one after another (see PromoCodeService.validateForOrder).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PromoCode p WHERE p.code = :code")
    Optional<PromoCode> findByCodeForUpdate(@Param("code") String code);
}