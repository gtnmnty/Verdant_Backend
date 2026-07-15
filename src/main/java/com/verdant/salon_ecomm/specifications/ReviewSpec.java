package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.entities.Review;
import com.verdant.salon_ecomm.models.enums.ItemType;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class ReviewSpec {

    public static Specification<Review> targetIs(ItemType targetType, UUID targetId) {
        return (root, query, cb) -> cb.and(
            cb.equal(root.get("targetType"), targetType),
            cb.equal(root.get("targetId"), targetId)
        );
    }

    public static Specification<Review> starsEquals(Short stars) {
        if (stars == null) return null;
        return (root, query, cb) ->
            cb.equal(root.get("stars"), stars);
    }

    public static Specification<Review> hasItemType(ItemType itemType) {
        if (itemType == null) return null;
        return (root, query, cb) ->
            cb.equal(root.get("targetType"), itemType);
    }

    public static Specification<Review> matchesSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("text")), pattern),
            cb.like(cb.lower(root.join("user", JoinType.LEFT).get("fullName")), pattern)
        );
    }
}