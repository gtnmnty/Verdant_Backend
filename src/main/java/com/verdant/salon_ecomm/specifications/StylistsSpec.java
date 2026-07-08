package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.models.enums.StylistAccountStatus;
import com.verdant.salon_ecomm.models.enums.StylistAvailability;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

public class StylistsSpec {

    public static Specification<Stylist> filter(
        StylistAccountStatus status, String branch, String search, List<SalonService> services
    ) {
        return Specification.allOf(
            hasBranch(branch), hasSearch(search),
            hasStatus(status), hasServiceOf(services)
        );
    }

    public static Specification<Stylist> hasStatus(StylistAccountStatus status) {
        if (status == null) {
            return null;
        }

        return (root, query, cb) ->
            cb.equal(root.get("status"), status);
    }

    public static Specification<Stylist> hasBranch(String branch) {
        if (branch == null || branch.isBlank()) {
            return null;
        }

        return (root, query, cb) ->
            cb.equal(root.get("branch"), branch);
    }

    public static Specification<Stylist> hasSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) ->
            cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("bio")), pattern)
            );
    }

    public static Specification<Stylist> hasServiceOf(List<SalonService> services) {
        if (services == null || services.isEmpty()) {
            return null;
        }

        return (root, query, cb) -> {
            query.distinct(true);
            Join<Stylist, SalonService> join = root.join("services");
            return join.in(services);
        };
    }

}