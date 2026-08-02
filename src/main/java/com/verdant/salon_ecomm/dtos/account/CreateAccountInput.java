package com.verdant.salon_ecomm.dtos.account;

import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;

public record CreateAccountInput(
    String fullName,
    String email,
    String phone,
    AddressInput address,
    AccountRole role,
    AccountStatus status
) {}
