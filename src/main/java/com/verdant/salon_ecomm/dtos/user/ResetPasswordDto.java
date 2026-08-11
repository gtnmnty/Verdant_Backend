package com.verdant.salon_ecomm.dtos.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordDto {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Reset code is required")
    private String code;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least {min} characters long")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, and one number"
    )
    private String newPassword;
}
