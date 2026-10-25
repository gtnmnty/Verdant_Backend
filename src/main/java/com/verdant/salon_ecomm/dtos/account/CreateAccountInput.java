package com.verdant.salon_ecomm.dtos.account;

import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// M-10: checked by @Valid in AccountResolver.createAccount. Sizes match the users columns
// (full_name 100, email 255, phone 20); oversized values used to fail at insert time as a 500.
public record CreateAccountInput(
    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must be at most 100 characters")
    String fullName,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must be at most 255 characters")
    String email,

    @Size(max = 20, message = "Phone must be at most 20 characters")
    String phone,

    // Optional, but when sent it must be complete (AddressInput's own constraints).
    @Valid
    AddressInput address,

    @NotNull(message = "Role is required")
    AccountRole role,

    AccountStatus status
) {}
