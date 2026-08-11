package com.verdant.salon_ecomm.dtos.user;

import com.verdant.salon_ecomm.dtos.AddressInput;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class UpdateUserRequest {
    private String fullName;
    private String email;
    private String password;
    private String phoneNumber;

    @Valid
    private AddressInput shippingAddress;
}
