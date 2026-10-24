package com.verdant.salon_ecomm.dtos.user;

import com.verdant.salon_ecomm.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class RegisterUserDto {
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @ValidPassword
    @ToString.Exclude
    private String password;
    private String phoneNumber;
}
