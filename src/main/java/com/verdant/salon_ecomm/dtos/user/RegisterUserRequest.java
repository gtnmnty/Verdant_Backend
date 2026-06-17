package com.verdant.salon_ecomm.dtos.user;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@AllArgsConstructor
@Data
public class RegisterUserRequest {
    private UUID id;
    private String fullName;
    private String email;
    private String password;
}
