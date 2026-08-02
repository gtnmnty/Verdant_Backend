package com.verdant.salon_ecomm.dtos.account;

import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;

public record AccountFilterInput(
    String search,
    AccountRole role,
    AccountStatus status
) {}
