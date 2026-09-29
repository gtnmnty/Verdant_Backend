package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.user.RegisterUserDto;
import com.verdant.salon_ecomm.dtos.user.UserDto;
import com.verdant.salon_ecomm.dtos.user.UpdateUserRequest;
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

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "phone", source = "phoneNumber")
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "role", constant = "CUSTOMER")
    @Mapping(target = "emailVerified", constant = "false")
    @Mapping(target = "enabled", constant = "false")   // was "active" — field is "enabled"
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(RegisterUserDto request);

    UserDto toDto(User user);

    @Mapping(target = "shippingAddress", source = "address")
    UserDto.Profile toProfile(User user);

    // Entity → minimal embed (e.g. inside OrderResponse)
    UserDto.Summary toSummary(User user);

    // Entity → admin view
    UserDto.Admin toAdmin(User user);


    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "phone", source = "phoneNumber")
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "address", source = "shippingAddress")
    void updateEntity(UpdateUserRequest request, @MappingTarget User user);

    // OffsetDateTime → Instant converter (used for createdAt / updatedAt in DTOs)
    default Instant map(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}