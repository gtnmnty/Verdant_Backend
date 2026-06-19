package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.user.RegisterUserRequest;
import com.verdant.salon_ecomm.dtos.user.UpdateUserRequest;
import com.verdant.salon_ecomm.dtos.user.UserResponse;
import com.verdant.salon_ecomm.entities.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public abstract class UserMapper {
    public abstract UserResponse toDto(User user);

    @Mapping(source = "password", target = "passwordHash")
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "address.line1", source = "addressLine1")
    @Mapping(target = "address.line2", source = "addressLine2")
    @Mapping(target = "address.city", source = "addressCity")
    @Mapping(target = "address.state", source = "addressState")
    @Mapping(target = "address.postal", source = "addressPostal")
    public abstract User toEntity(RegisterUserRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    public abstract void updateUser(UpdateUserRequest request, @MappingTarget User user);
}
