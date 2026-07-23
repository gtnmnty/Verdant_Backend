package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.models.enums.OrderStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Filter builder for the higher-role orders table (image 3):
 * search by reference/order id or customer name, and filter by status.
 */
public final class OrderSpec {

    private OrderSpec() {
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

            var idAsString = cb.function("CAST", String.class, root.get("id"), cb.literal("varchar"));
            // NOTE: adjust "firstName"/"lastName" below if your User entity
            // names customer fields differently (User.java wasn't provided).
            var userJoin = root.join("user", jakarta.persistence.criteria.JoinType.LEFT);

            return cb.or(
                    cb.like(cb.lower(idAsString), like),
                    cb.like(cb.lower(userJoin.get("firstName")), like),
                    cb.like(cb.lower(userJoin.get("lastName")), like)
            );
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
