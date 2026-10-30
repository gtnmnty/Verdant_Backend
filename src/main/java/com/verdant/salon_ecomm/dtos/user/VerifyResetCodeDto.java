package com.verdant.salon_ecomm.dtos.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

// "Forgot password" step 2 — check the emailed code before the user picks a new password.
@Data
public class VerifyResetCodeDto {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Reset code is required")
    @ToString.Exclude
    private String code;
}
