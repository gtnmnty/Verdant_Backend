package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import com.verdant.salon_ecomm.utils.EnumUtils;
import org.springframework.data.jpa.domain.Specification;

public class ServiceSpec {

    public static Specification<SalonService> filterSalonService(
        String category, String search, CollectionStatus status
    ){
        return Specification.allOf(
            hasCategory(category), hasSearch(search), hasStatus(status)
        );
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
                cb.like(cb.lower(root.get("category")), pattern)
            );
    }

    public static Specification<SalonService> hasStatus(CollectionStatus status){
        if(status == null) return null;
        return (root, query, cb) ->
            cb.equal(root.get("status"), status);
    }
}
