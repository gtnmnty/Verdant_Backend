package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.models.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    List<Order> findByUserId(UUID userId);

    List<Order> findByOrderStatus(OrderStatus orderStatus);

    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * "My Orders" page — recent orders (< 30 days old). Sort direction
     * (Newest/Oldest/Highest Total, image 1) comes from the Sort baked
     * into `pageable`, not the method name — see OrderSorts.
     */
    Page<Order> findByUserIdAndCreatedAtAfter(UUID userId, OffsetDateTime cutoff, Pageable pageable);

    /**
     * "View Older History" — orders 30+ days old, paged (image 2), same
     * sort-via-Pageable pattern as above.
     */
    Page<Order> findByUserIdAndCreatedAtBefore(UUID userId, OffsetDateTime cutoff, Pageable pageable);

    // Higher-role order list (image 3), paged + filtered by status/search
    // + sorted: use JpaSpecificationExecutor#findAll(Specification, Pageable),
    // already inherited above — see OrderSpecifications + OrderSorts.

    /**
     * Optional: substring search against the order id itself, since
     * casting UUID -> text isn't reliably expressible through the
     * Criteria API (Path.as(String.class) doesn't emit a SQL CAST in
     * Hibernate 6, and Postgres has no lower(uuid) overload). Not
     * currently called anywhere — OrderSpecifications.search() only
     * searches User.fullName. Wire this in (e.g. via a native-query
     * Specification, or by unioning IDs from this with the name-based
     * spec results) if id/reference search becomes a real requirement.
     */
    @Query(
        value = "SELECT * FROM orders WHERE CAST(id AS text) ILIKE CONCAT('%', :search, '%')",
        nativeQuery = true)
    List<Order> searchByIdText(@org.springframework.data.repository.query.Param("search") String search);
}