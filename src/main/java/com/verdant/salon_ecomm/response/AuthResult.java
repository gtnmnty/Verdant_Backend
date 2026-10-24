package com.verdant.salon_ecomm.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
public class AuthResult {
  @ToString.Exclude
  private String token;
  @JsonIgnore
  @ToString.Exclude
  private String refreshToken;
  // Access-token lifetime in milliseconds (a duration, not a timestamp).
  // Still serialized as "expiresAt" because the frontend reads that key.
  @JsonProperty("expiresAt")
  private long expiresInMs;
}
