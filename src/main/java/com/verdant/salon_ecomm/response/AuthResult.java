package com.verdant.salon_ecomm.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResult {
  private String token;
  @JsonIgnore
  private String refreshToken;
  private long expiresAt;
}
