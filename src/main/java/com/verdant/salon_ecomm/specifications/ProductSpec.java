package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import com.verdant.salon_ecomm.utils.EnumUtils;
import org.springframework.data.jpa.domain.Specification;

public class ProductSpec {

    public static Specification<Product> filter(String category, String search, CollectionStatus status) {
        return Specification.allOf(hasCategory(category), hasSearch(search), hasStatus(status));
    }

    private static Specification<Product> hasCategory(String category) {
        if (category == null || category.isBlank()) return null;
        ItemCatalog parsed = EnumUtils.parseEnum(ItemCatalog.class, category, "category");
        return (root, query, cb) -> cb.equal(root.get("itemCatalog"), parsed);
    }

    private static Specification<Product> hasStatus(CollectionStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    private static Specification<Product> hasSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) ->
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("description")), pattern)
            );
    }
}
