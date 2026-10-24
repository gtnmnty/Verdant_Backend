package com.verdant.salon_ecomm.dtos.user;

import com.verdant.salon_ecomm.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class ResetPasswordDto {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Reset code is required")
    @ToString.Exclude
    private String code;

    @ValidPassword
    @ToString.Exclude
    private String newPassword;
}
