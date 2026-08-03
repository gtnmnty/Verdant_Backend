package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Order;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

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

    Optional<Order> findByStripePaymentIntentId(String paymentIntentId);

    @Query("SELECT DISTINCT o.user.id FROM Order o WHERE o.user.id IN :requestedIds")
    List<UUID> findDistinctUserIdsWithOrders(@Param("requestedIds") Set<UUID> requestedIds);
}