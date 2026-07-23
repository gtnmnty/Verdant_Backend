package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.models.enums.OrderStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> withFilters(String search, OrderStatus status) {
        return Specification.allOf(
            search(search),
            hasStatus(status)
        );
    }

    public static Specification<Order> search(String search) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(search)) {
                return cb.conjunction();
            }
            String like = "%" + search.trim().toLowerCase() + "%";

            var userJoin = root.join("user", jakarta.persistence.criteria.JoinType.LEFT);

            return cb.like(cb.lower(userJoin.get("fullName")), like);
        };
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> {
            if (status == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get("orderStatus"), status);
        };
    }
}