package com.verdant.salon_ecomm.dtos.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

// M-10: had no constraints, so a missing email caused an NPE in AuthController and a 500.
// Now @Valid in AuthController.logIn rejects it with a 400 before any lookup runs.
@Data
public class LogInUserDto {
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @ToString.Exclude
    private String password;
}
