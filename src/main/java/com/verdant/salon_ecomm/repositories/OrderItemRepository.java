package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderId(UUID orderId);

    // Batch fetch for list views (My Orders, admin table) — avoids the
    // N+1 of calling findByOrderId once per order on a page.
    List<OrderItem> findByOrderIdIn(List<UUID> orderIds);
}
