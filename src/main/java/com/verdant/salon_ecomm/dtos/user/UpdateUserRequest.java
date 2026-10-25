package com.verdant.salon_ecomm.dtos.user;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.verdant.salon_ecomm.dtos.AddressInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@AllArgsConstructor
@Data
public class UpdateUserRequest {
    private String fullName;

    // A different value here does not change the email directly: it starts a
    // pending change that the user confirms with a code sent to the new address.
    @Email
    @Size(max = 255)
    private String email;

    // M-23: was "phoneNumber", but the GraphQL input UpdateProfileInput calls it "phone". Spring GraphQL
    // binds input fields by name, so the phone was never bound and every phone change was silently dropped.
    // Renamed to match the schema; @JsonAlias keeps old REST clients that still send "phoneNumber" working.
    // Max 20 = users.phone column.
    @JsonAlias("phoneNumber")
    @Size(max = 20)
    private String phone;

    // M-23: the unused "password" field was removed. Nothing read it, and a password sent through the
    // profile form looked accepted but was ignored. Password changes go through changePassword.

    // Required only when the email is being changed, so a stolen access token alone can't move the account.
    @ToString.Exclude
    private String currentPassword;

    @Valid
    private AddressInput shippingAddress;
}
