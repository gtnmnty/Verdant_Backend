package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.entities.Review;
import com.verdant.salon_ecomm.models.enums.ItemType;
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
    if (stars == null) return null; // Specification.allOf() ignores nulls — matches your ProductSpec pattern
    return (root, query, cb) -> cb.equal(root.get("stars"), stars);
  }
}
