package com.verdant.salon_ecomm.dtos.user;

import com.verdant.salon_ecomm.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

// M-10: sizes match the users columns (full_name 100, email 255, phone 20). fullName is NOT NULL in
// the DB; unvalidated, a missing name sent the verification email and then failed the insert (500).
@Data
public class RegisterUserDto {
    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must be at most 100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    @ValidPassword
    @ToString.Exclude
    private String password;

    @Size(max = 20, message = "Phone number must be at most 20 characters")
    private String phoneNumber;
}
