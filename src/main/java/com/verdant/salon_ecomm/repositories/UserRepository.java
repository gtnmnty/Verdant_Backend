package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.models.enums.accounts.ResetLinkStatus;
import com.verdant.salon_ecomm.utils.EmailNormalizer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // M-36: every email lookup normalizes the input (trim + lower-case) and compares against lower(email), so
    // "Jane@X.com " finds the account stored as "jane@x.com". These were exact-match derived queries, so the
    // case a user typed at signup was the only one that worked at login. lower(email) also matches the
    // unique expression index from V12, so the lookups stay indexed.
    // Default methods: Spring Data calls them as written and doesn't try to derive a query from their name.
    default boolean existsByEmail(String email) {
        return existsByNormalizedEmail(EmailNormalizer.normalize(email));
    }

    default Optional<User> findByEmail(String email) {
        return findByNormalizedEmail(EmailNormalizer.normalize(email));
    }

    default Optional<User> findByEmailForUpdate(String email) {
        return findByNormalizedEmailForUpdate(EmailNormalizer.normalize(email));
    }

    // The three queries below expect an already normalized email; call the methods above instead.
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE lower(u.email) = :email")
    boolean existsByNormalizedEmail(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE lower(u.email) = :email")
    Optional<User> findByNormalizedEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE lower(u.email) = :email")
    Optional<User> findByNormalizedEmailForUpdate(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    // H-9: written by the async email listener, which runs outside any transaction and after the
    // issuing one has committed. A targeted UPDATE touches only these two columns, so it can't
    // overwrite anything else changed on the user in the meantime.
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.resetLinkStatus = :status, u.resetLinkStatusAt = :at WHERE u.id = :id")
    int updateResetLinkStatus(@Param("id") UUID id, @Param("status") ResetLinkStatus status,
                              @Param("at") OffsetDateTime at);

    // M-38: who receives staff notifications (StaffNotifier). Replaces findByRoleIn, which ignored the account
    // status, so suspended, banned and deleted staff kept getting notifications. Ids only: the bulk insert
    // links each notification by reference and never needs the full user rows.
    @Query("SELECT u.id FROM User u WHERE u.role IN :roles AND u.status = :status")
    List<UUID> findIdsByRoleInAndStatus(@Param("roles") Collection<AccountRole> roles,
                                        @Param("status") AccountStatus status);
}