package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.models.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByUserId(UUID userId);
    List<Order> findByOrderStatus(OrderStatus orderStatus);
    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
