package com.verdant.salon_ecomm.dtos.account;

import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;

import java.util.UUID;

public record AccountDto(
    UUID id,
    String fullName,
    String email,
    String phone,
    String avatarUrl,
    AccountRole role,
    AccountStatus status
) {}
