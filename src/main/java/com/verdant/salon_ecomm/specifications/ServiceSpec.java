package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import com.verdant.salon_ecomm.utils.EnumUtils;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;

public class ServiceSpec {

    public static Specification<SalonService> filterSalonService(
        String category, String search, CollectionStatus status
    ){
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (category != null && !category.isBlank()) {
                ItemCatalog catalogEnum = EnumUtils.parseEnum(ItemCatalog.class, category, search);
                predicates.add(cb.equal(root.get("itemCatalog"), catalogEnum));
            }
            if (search != null) predicates.add(cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%"));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<SalonService> hasCategory(String category){
        if(category == null || category.isEmpty()) return null;
        ItemCatalog parsed = EnumUtils.parseEnum(ItemCatalog.class, category, "category");
        return (root, query, cb) ->
            cb.equal(root.get("itemCatalog"), parsed);
    }

    public static Specification<SalonService> hasSearch(String search){
        if (search == null || search.isBlank()) return null;
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) ->
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("itemCatalog").as(String.class)), pattern)
            );
    }

    public static Specification<SalonService> hasStatus(CollectionStatus status){
        if(status == null) return null;
        return (root, query, cb) ->
            cb.equal(root.get("status"), status);
    }
}
