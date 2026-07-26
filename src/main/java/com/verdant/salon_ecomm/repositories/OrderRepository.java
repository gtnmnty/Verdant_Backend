package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    @Query(value = "SELECT nextval('order_code_seq')", nativeQuery = true)
    Long getNextOrderCodeSequenceValue();

    Optional<Order> findByStripePaymentIntentId(String paymentIntentId);
}