package com.verdant.salon_ecomm.dtos.user;

import lombok.Data;

@Data
public class VerifyUserDto {
    private String email;
    private String verificationCode;
}
