package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.models.entities.SalonService;
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
                ItemCatalog catalogEnum = EnumUtils.parseEnum(ItemCatalog.class, category, "category");
                predicates.add(cb.equal(root.get("itemCatalog"), catalogEnum));
            }
            if (search != null && !search.isBlank()) predicates.add(cb.like(cb.lower(root.get("name")), LikePatterns.contains(search), LikePatterns.ESCAPE));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
