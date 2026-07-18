package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.entities.Branch;
import com.verdant.salon_ecomm.models.enums.BranchStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class BranchSpecification {

    public static Specification<Branch> hasStatus(BranchStatus status) {
        return (root, query, cb) ->
            status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Branch> matchesSearch(String searchTerm) {
        return (root, query, cb) -> {
            if (searchTerm == null || searchTerm.isBlank()) {
                return null;
            }
            String pattern = "%" + searchTerm.trim().toLowerCase() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("phone")), pattern),
                cb.like(cb.lower(root.get("address").get("line1")), pattern),
                cb.like(cb.lower(root.get("address").get("city")), pattern)
            );
        };
    }

    public static Specification<Branch> filterBy(BranchStatus status, String searchTerm) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            Specification<Branch> statusSpec = hasStatus(status);
            if (statusSpec != null) {
                Predicate p = statusSpec.toPredicate(root, query, cb);
                if (p != null) predicates.add(p);
            }

            Specification<Branch> searchSpec = matchesSearch(searchTerm);
            if (searchSpec != null) {
                Predicate p = searchSpec.toPredicate(root, query, cb);
                if (p != null) predicates.add(p);
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
