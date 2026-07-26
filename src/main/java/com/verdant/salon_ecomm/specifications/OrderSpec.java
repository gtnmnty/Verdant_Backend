package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import com.verdant.salon_ecomm.models.enums.orders.OrderClientFilter;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.UUID;

public class OrderSpec {

    public static Specification<Order> filterAdminOrders(
        OrderStatus status, String search
    ) {
        return Specification.allOf(
            hasStatus(status),
            matchesSearch(search)
        );
    }

    public static Specification<Order> filterMyOrders(
        UUID userId, OrderClientFilter clientFilter,
        String search, OffsetDateTime windowStart, OffsetDateTime windowEnd, boolean archived
    ) {
        return Specification.allOf(
            belongsToUser(userId),
            hasClientFilter(clientFilter),
            matchesSearch(search),
            archived ? outsideWindow(windowStart, windowEnd) : withinWindow(windowStart, windowEnd)
        );
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("orderStatus"), status);
    }

    public static Specification<Order> matchesSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("orderCode")), pattern);
    }

    public static Specification<Order> belongsToUser(UUID userId) {
        if (userId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Order> hasClientFilter(OrderClientFilter clientFilter) {
        if (clientFilter == null || clientFilter == OrderClientFilter.ALL) return null;
        return hasStatus(OrderStatus.valueOf(clientFilter.name()));
    }

    public static Specification<Order> withinWindow(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) return null;
        return (root, query, cb) -> cb.between(root.get("createdAt"), start, end);
    }

    public static Specification<Order> outsideWindow(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) return null;
        return (root, query, cb) -> cb.or(
            cb.lessThan(root.get("createdAt"), start),
            cb.greaterThan(root.get("createdAt"), end)
        );
    }

}