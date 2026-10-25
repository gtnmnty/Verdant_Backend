package com.verdant.salon_ecomm.dtos.user;

import com.verdant.salon_ecomm.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@AllArgsConstructor
@Data
public class ChangePasswordRequest {
    // A null here made BCrypt throw IllegalArgumentException (500) instead of a 400.
    @NotBlank(message = "Current password is required")
    @ToString.Exclude
    String oldPassword;

    @ValidPassword
    @ToString.Exclude
    String newPassword;
}
