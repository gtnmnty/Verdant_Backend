package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.id = :id")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT u FROM User u WHERE u.role IN :staffRoles")
    List<User> findByRoleIn(@Param("staffRoles") List<AccountRole> staffRoles);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    // Atomically redeems a reset code: the UPDATE only takes effect if the code
    // still matches and hasn't expired, so two concurrent requests racing to use
    // the same code can't both succeed — the second one affects 0 rows.
    @Modifying
    @Query("""
        UPDATE User u
        SET u.passwordHash = :newPasswordHash,
            u.resetPasswordCode = null,
            u.resetPasswordCodeExpiration = null
        WHERE u.email = :email
          AND u.resetPasswordCode = :code
          AND u.resetPasswordCodeExpiration > :now
        """)
    int consumePasswordResetCode(
        @Param("email") String email,
        @Param("code") String code,
        @Param("newPasswordHash") String newPasswordHash,
        @Param("now") OffsetDateTime now
    );
}