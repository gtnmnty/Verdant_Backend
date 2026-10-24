package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.dtos.user.UserDto;
import com.verdant.salon_ecomm.dtos.user.UpdateUserRequest;
import com.verdant.salon_ecomm.models.entities.Address;
import com.verdant.salon_ecomm.models.entities.User;
import org.mapstruct.*;

import java.time.Instant;
import java.time.OffsetDateTime;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserMapper {

    @Mapping(target = "shippingAddress", source = "address")
    UserDto.Profile toProfile(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "phone", source = "phoneNumber")
    @Mapping(target = "passwordHash", ignore = true)
    // Replaced as a whole in replaceAddress(), so a field left empty (e.g. line2) is cleared
    // instead of being skipped by the IGNORE strategy and keeping its old value.
    @Mapping(target = "address", ignore = true)
    // Email goes through UserService's verified pending-change flow, never a straight copy.
    @Mapping(target = "email", ignore = true)
    void updateEntity(UpdateUserRequest request, @MappingTarget User user);

    Address toAddress(AddressInput input);

    @AfterMapping
    default void replaceAddress(UpdateUserRequest request, @MappingTarget User user) {
        if (request.getShippingAddress() != null) {
            user.setAddress(toAddress(request.getShippingAddress()));
        }
    }

    // OffsetDateTime → Instant converter (used for createdAt / updatedAt in DTOs)
    default Instant map(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
