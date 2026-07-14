package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.entities.CartItem;
import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

public class CartItemSpec {

    public static Specification<CartItem> hasUser(UUID userId) {
        return (root, query, cb) ->
            cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<CartItem> hasIds(List<UUID> ids) {
        return (root, query, cb) ->
            root.get("id").in(ids);
    }

    public static Specification<CartItem> hasProduct(UUID productId) {
        return (root, query, cb) ->
            cb.equal(root.get("product").get("id"), productId);
    }

    public static Specification<CartItem> hasDeliveryOption(DeliveryOption deliveryOption) {
        return (root, query, cb) -> 
            cb.equal(root.get("deliveryOption"), deliveryOption);
    }

    // Scopes to a user's cart, optionally narrowed to a specific set of cart item ids.
    // `ids == null` means "no id filter" (whole cart). A non-null list is always applied
    // literally, so an empty selection correctly matches zero rows instead of silently
    // falling back to the user's entire cart.
    public static Specification<CartItem> build(UUID userId, List<UUID> ids) {
        Specification<CartItem> spec = hasUser(userId);
        if (ids != null) {
            spec = spec.and(hasIds(ids));
        }
        return spec;
    }
}