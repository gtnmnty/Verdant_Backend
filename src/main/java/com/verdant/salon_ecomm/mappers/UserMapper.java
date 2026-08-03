package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.user.RegisterUserDto;
import com.verdant.salon_ecomm.dtos.user.UserDto;
import com.verdant.salon_ecomm.dtos.user.UpdateUserRequest;
import com.verdant.salon_ecomm.entities.User;
import org.mapstruct.*;

import java.time.Instant;
import java.time.OffsetDateTime;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserMapper {

    // Registration → new entity (password is set separately after encoding)
    // BUG FIX: RegisterUserDto.phoneNumber and User.phone don't share a name, so
    // MapStruct's default matching silently dropped the phone number on every
    // registration. Explicit mapping required.
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

    // Entity → public profile
    UserDto.Profile toProfile(User user);

    // Entity → minimal embed (e.g. inside OrderResponse)
    UserDto.Summary toSummary(User user);

    // Entity → admin view
    UserDto.Admin toAdmin(User user);

    // Partial update — only non-null fields are applied.
    // BUG FIX: same phoneNumber/phone name mismatch as toEntity() above — every
    // profile update was silently discarding the new phone number.
    // BUG FIX: UpdateUserRequest.password has no matching User property
    // (User has passwordHash, not password), so it was already a no-op — but a
    // silent no-op on a field literally called "password" is exactly the kind of
    // thing that should fail loudly or not exist. Password changes belong in the
    // dedicated updateUserPassword() flow, which verifies the old password first;
    // this mapping explicitly ignores it here so the intent is visible in code
    // rather than relying on an accidental name mismatch.
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "phone", source = "phoneNumber")
    @Mapping(target = "passwordHash", ignore = true)
    void updateEntity(UpdateUserRequest request, @MappingTarget User user);

    // OffsetDateTime → Instant converter (used for createdAt / updatedAt in DTOs)
    default Instant map(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}