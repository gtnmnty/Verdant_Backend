package com.verdant.salon_ecomm.dtos.user;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@AllArgsConstructor
@Data
public class UserResponse {
    private final UUID id;
    private final String fullName;
    private final String email;
}
