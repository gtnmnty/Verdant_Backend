package com.verdant.salon_ecomm.dtos.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// M-5 / M-10: AuthController keys the per-address rate limit on this email, so it must be present.
@Data
public class ResendVerificationCodeDto {
  @NotBlank(message = "Email is required")
  @Email(message = "Email must be valid")
  private String email;
}
