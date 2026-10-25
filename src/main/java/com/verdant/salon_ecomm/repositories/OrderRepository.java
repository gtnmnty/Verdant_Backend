package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT nextval('order_code_seq')", nativeQuery = true)
    Long getNextOrderCodeSequenceValue();

    // M-15: SELECT ... FOR UPDATE for the Stripe webhook. Two events for the same intent delivered at the
    // same time (e.g. processing and succeeded) used to both read PENDING, and the last commit won. Now the
    // second waits for the first to commit and then sees its status, so the terminal-status check works.
    // Also serializes the webhook with an admin edit, which locks the same row via findByIdForUpdate.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.stripePaymentIntentId = :paymentIntentId")
    Optional<Order> findByStripePaymentIntentIdForUpdate(@Param("paymentIntentId") String paymentIntentId);

    @Query("SELECT DISTINCT o.user.id FROM Order o WHERE o.user.id IN :requestedIds")
    List<UUID> findDistinctUserIdsWithOrders(@Param("requestedIds") Set<UUID> requestedIds);

    // H-5: atomic once-only claim. Returns 1 for the caller that should restock, 0 if another
    // path already did. The caller must also set the flag on its managed entity, or a later
    // save() of that entity would write the stale false back.
    // M-15: no version bump here on purpose: the caller saves the same Order entity later in this
    // transaction, and bumping the version under it would make that save fail its version check.
    @Modifying
    @Query("UPDATE Order o SET o.stockReleased = true WHERE o.id = :id AND o.stockReleased = false")
    int claimStockRelease(@Param("id") UUID id);

    // Reverse of the claim, for an admin re-opening a cancelled order (stock is reserved again first).
    @Modifying
    @Query("UPDATE Order o SET o.stockReleased = false WHERE o.id = :id AND o.stockReleased = true")
    int unclaimStockRelease(@Param("id") UUID id);

    // Open unpaid orders still holding stock: the per-user cap in placeOrder.
    @Query("""
        SELECT COUNT(o) FROM Order o
        WHERE o.user.id = :userId AND o.orderStatus = :status
          AND o.paymentStatus IN :paymentStatuses AND o.stockReleased = false
        """)
    long countOpenUnpaid(@Param("userId") UUID userId,
                         @Param("status") OrderStatus status,
                         @Param("paymentStatuses") Collection<PaymentStatus> paymentStatuses);

    // Candidates for UnpaidOrderExpiryJob, oldest first.
    @Query("""
        SELECT o FROM Order o
        WHERE o.orderStatus = :status AND o.paymentStatus IN :paymentStatuses
          AND o.stockReleased = false AND o.createdAt < :cutoff
        ORDER BY o.createdAt ASC
        """)
    List<Order> findExpiredUnpaid(@Param("status") OrderStatus status,
                                  @Param("paymentStatuses") Collection<PaymentStatus> paymentStatuses,
                                  @Param("cutoff") OffsetDateTime cutoff,
                                  Pageable pageable);
}