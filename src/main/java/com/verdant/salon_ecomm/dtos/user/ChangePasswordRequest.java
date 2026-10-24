package com.verdant.salon_ecomm.dtos.user;

import com.verdant.salon_ecomm.validation.ValidPassword;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@AllArgsConstructor
@Data
public class ChangePasswordRequest {
    @ToString.Exclude
    String oldPassword;

    @ValidPassword
    @ToString.Exclude
    String newPassword;
}
