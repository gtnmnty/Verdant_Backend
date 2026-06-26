package com.verdant.salon_ecomm.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
  private String token;
  private String refreshToken;
  private long expiresAt;
}
