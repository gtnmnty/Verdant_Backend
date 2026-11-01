package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.PendingCheckout;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingCheckoutRepository extends JpaRepository<PendingCheckout, UUID> {

    // Finalize (frontend) and the Stripe webhook can both try to turn the same draft into an order;
    // the row lock makes the second wait, then find the draft gone and the order already there.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PendingCheckout d where d.id = :id")
    Optional<PendingCheckout> findByIdForUpdate(@Param("id") UUID id);

    Optional<PendingCheckout> findByStripePaymentIntentId(String stripePaymentIntentId);

    // Customer's own earlier drafts (not staff-created ones), replaced when they open the card form again.
    List<PendingCheckout> findByUserIdAndCreatedByUserIdIsNull(UUID userId);

    @Query("select d from PendingCheckout d where d.expiresAt < :cutoff order by d.expiresAt asc")
    List<PendingCheckout> findExpired(@Param("cutoff") OffsetDateTime cutoff, Pageable pageable);
}
