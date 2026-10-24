package com.verdant.salon_ecomm.dtos.user;

import lombok.Data;
import lombok.ToString;

@Data
public class LogInUserDto {
    private String email;
    @ToString.Exclude
    private String password;
}
