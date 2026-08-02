package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    long countByRoleAndStatus(AccountRole role, AccountStatus status);
}
