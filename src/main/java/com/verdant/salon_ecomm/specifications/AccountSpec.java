package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import org.springframework.data.jpa.domain.Specification;

public class AccountSpec {

    public static Specification<User> filterAccounts(
        AccountStatus status, AccountRole role, String search
    ) {
        return Specification.allOf(
            hasStatus(status),
            hasRole(role),
            matchesSearch(search)
        );
    }

    public static Specification<User> hasStatus(AccountStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<User> hasRole(AccountRole role) {
        if (role == null) return null;
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    public static Specification<User> matchesSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("fullName")), pattern),
            cb.like(cb.lower(root.get("email")), pattern),
            cb.like(cb.lower(root.get("phone")), pattern)
        );
    }

    public static Specification<User> hasEmail(String email) {
        if (email == null || email.isBlank()) return null;
        return (root, query, cb) -> cb.equal(cb.lower(root.get("email")), email.toLowerCase());
    }
}
