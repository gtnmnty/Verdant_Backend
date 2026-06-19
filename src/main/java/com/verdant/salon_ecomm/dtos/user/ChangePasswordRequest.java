package com.verdant.salon_ecomm.dtos.user;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class ChangePasswordRequest {
    String oldPassword;
    String newPassword;
}
