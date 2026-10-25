package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.SalonService;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface SalonServiceRepository extends JpaRepository<SalonService, UUID>, JpaSpecificationExecutor<SalonService> {
    @Modifying
    @Query("UPDATE SalonService s SET s.reviewCount = :count, s.averageRating = :avg WHERE s.id = :id")
    int updateReviewAggregates(@Param("id") UUID id, @Param("count") int count, @Param("avg") BigDecimal avg);

    // M-32 / M-40: SELECT ... FOR UPDATE on the service row, used as a per-service mutex for review
    // submissions and image changes (same as ProductRepository.findByIdForUpdate).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SalonService s WHERE s.id = :id")
    Optional<SalonService> findByIdForUpdate(@Param("id") UUID id);
}
