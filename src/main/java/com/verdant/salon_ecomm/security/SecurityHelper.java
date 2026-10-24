package com.verdant.salon_ecomm.security;

import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

// The one place resolvers and services ask "who is calling" and "what may they see".
// Prefer @AuthenticationPrincipal in resolver signatures; currentUserId() is for code that can't take it.
@Component
public class SecurityHelper {

    // Staff who may act on any customer's records (orders, appointments, ...).
    private static final Set<AccountRole> STAFF_ROLES = EnumSet.of(
        AccountRole.RECEPTIONIST, AccountRole.MANAGER, AccountRole.ADMIN, AccountRole.OWNER);

    // Staff who may also handle money on a customer's behalf (e.g. create a payment intent for their order).
    private static final Set<AccountRole> MANAGEMENT_ROLES = EnumSet.of(
        AccountRole.MANAGER, AccountRole.ADMIN, AccountRole.OWNER);

    public UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        throw new IllegalStateException("No authenticated user found");
    }

    public boolean isStaff(User user) {
        return user != null && STAFF_ROLES.contains(user.getRole());
    }

    public boolean isManagement(User user) {
        return user != null && MANAGEMENT_ROLES.contains(user.getRole());
    }
}
