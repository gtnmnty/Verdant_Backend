package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import org.springframework.data.jpa.domain.Specification;

public class ProductSpec {

  public static Specification<Product> filter(String category, String search, Boolean isActive) {
    return Specification.allOf(hasCategory(category), hasSearch(search), hasStatus(isActive));
  }

  private static Specification<Product> hasCategory(String category) {
    if (category == null || category.isBlank()) return null;
    return (root, query, cb) ->
            cb.equal(root.get("itemCatalog"), ItemCatalog.valueOf(category));
  }

  private static Specification<Product> hasStatus(Boolean isActive) {
    if (isActive == null) return null;
    return (root, query, cb) -> cb.equal(root.get("isActive"), isActive);
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
