package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.ResetLinkStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    // H-9: written by the async email listener, which runs outside any transaction and after the
    // issuing one has committed. A targeted UPDATE touches only these two columns, so it can't
    // overwrite anything else changed on the user in the meantime.
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.resetLinkStatus = :status, u.resetLinkStatusAt = :at WHERE u.id = :id")
    int updateResetLinkStatus(@Param("id") UUID id, @Param("status") ResetLinkStatus status,
                              @Param("at") OffsetDateTime at);

    @Query("SELECT u FROM User u WHERE u.role IN :staffRoles")
    List<User> findByRoleIn(@Param("staffRoles") List<AccountRole> staffRoles);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);
}