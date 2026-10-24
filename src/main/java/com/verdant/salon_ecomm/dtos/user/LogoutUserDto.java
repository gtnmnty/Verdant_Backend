package com.verdant.salon_ecomm.dtos.user;

import lombok.Data;
import lombok.ToString;

@Data
public class LogoutUserDto {
  @ToString.Exclude
  private String refreshToken;
}
