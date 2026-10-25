package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.config.SecurityConfig;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// H-2: AccountService.ROLE_RANK decides who may manage/assign whom, while Spring's RoleHierarchy
// decides what each role can actually do. If they disagree, an account can be handed authorities
// its creator doesn't hold. Plain unit test, no Spring context or database needed.
class RoleRankHierarchySyncTest {

    private final RoleHierarchy hierarchy = SecurityConfig.roleHierarchy();

    @Test
    void everyRoleHasARank() {
        assertThat(AccountService.ROLE_RANK.keySet()).containsExactlyInAnyOrder(AccountRole.values());
    }

    @Test
    void rankOrderMatchesRoleHierarchy() {
        for (AccountRole senior : AccountRole.values()) {
            Set<String> reachable = reachableAuthorities(senior);
            for (AccountRole other : AccountRole.values()) {
                if (other == senior) continue;
                // If Spring lets `senior` act as `other`, the rank table must also rank it above `other`.
                if (reachable.contains("ROLE_" + other.name())) {
                    assertThat(AccountService.ROLE_RANK.get(senior))
                        .as("%s implies %s in RoleHierarchy, so it must rank above it", senior, other)
                        .isLessThan(AccountService.ROLE_RANK.get(other));
                }
            }
        }
    }

    @Test
    void lowerRankedRolesNeverImplyHigherRankedOnes() {
        for (AccountRole junior : AccountRole.values()) {
            Set<String> reachable = reachableAuthorities(junior);
            for (AccountRole other : AccountRole.values()) {
                if (AccountService.ROLE_RANK.get(other) < AccountService.ROLE_RANK.get(junior)) {
                    assertThat(reachable)
                        .as("%s ranks below %s, so the hierarchy must not grant it %s's authority", junior, other, other)
                        .doesNotContain("ROLE_" + other.name());
                }
            }
        }
    }

    private Set<String> reachableAuthorities(AccountRole role) {
        List<GrantedAuthority> granted = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        return hierarchy.getReachableGrantedAuthorities(granted).stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
    }
}
