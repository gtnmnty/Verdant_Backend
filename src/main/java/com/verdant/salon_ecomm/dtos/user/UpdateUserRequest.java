package com.verdant.salon_ecomm.dtos.user;

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

    @ToString.Exclude
    private String password;
    private String phoneNumber;

    // Required only when the email is being changed, so a stolen access token alone can't move the account.
    @ToString.Exclude
    private String currentPassword;

    @Valid
    private AddressInput shippingAddress;
}
