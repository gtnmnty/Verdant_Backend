package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.dtos.account.AccountDetailDto;
import com.verdant.salon_ecomm.dtos.account.AccountDto;
import com.verdant.salon_ecomm.dtos.account.CreateAccountInput;
import com.verdant.salon_ecomm.dtos.account.UpdateAccountInput;
import com.verdant.salon_ecomm.entities.Address;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountMapper {

    public AccountDto toResponse(User user) {
        return new AccountDto(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getPhone(),
            user.getAvatarUrl(),
            user.getRole(),
            user.getStatus()
        );
    }

    public AccountDetailDto toDetailResponse(User user) {
        return new AccountDetailDto(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getPhone(),
            user.getAddress(),
            user.getAvatarUrl(),
            user.getRole(),
            user.getStatus(),
            user.getCreatedAt()
        );
    }

    public User toEntity(CreateAccountInput input, String encodedPassword) {
        User user = new User();
        user.setFullName(input.fullName());
        user.setEmail(input.email());
        user.setPhone(input.phone());
        user.setPasswordHash(encodedPassword);
        user.setRole(input.role());
        user.setStatus(input.status() != null ? input.status() : AccountStatus.UNVERIFIED);
        user.setAddress(toAddress(input.address()));
        return user;
    }

    public void updateEntity(User user, UpdateAccountInput input) {
        if (input.fullName() != null) user.setFullName(input.fullName());
        if (input.email() != null) user.setEmail(input.email());
        if (input.phone() != null) user.setPhone(input.phone());
        if (input.role() != null) user.setRole(input.role());
        if (input.status() != null) user.setStatus(input.status());
        if (input.address() != null) user.setAddress(toAddress(input.address()));
    }

    private Address toAddress(AddressInput addressInput) {
        if (addressInput == null) return null;
        return Address.builder()
            .line1(addressInput.line1())
            .line2(addressInput.line2())
            .city(addressInput.city())
            .state(addressInput.state())
            .postal(addressInput.postal())
            .country(addressInput.country())
            .build();
    }
}
