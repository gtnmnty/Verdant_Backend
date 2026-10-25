package com.verdant.salon_ecomm.dtos.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

// M-10: without these, a missing email reached AuthController's rate-limit key and threw an NPE (500).
@Data
public class VerifyUserDto {
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Verification code is required")
    @ToString.Exclude
    private String verificationCode;
}
