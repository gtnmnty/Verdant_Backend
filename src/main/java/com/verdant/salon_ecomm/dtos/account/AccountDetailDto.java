package com.verdant.salon_ecomm.dtos.account;

import com.verdant.salon_ecomm.entities.Address;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountDetailDto(
    UUID id,
    String fullName,
    String email,
    String phone,
    Address address,
    String avatarUrl,
    AccountRole role,
    AccountStatus status,
    OffsetDateTime createdAt
) {}
