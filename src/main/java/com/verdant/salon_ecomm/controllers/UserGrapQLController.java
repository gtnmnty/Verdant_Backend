package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.user.UpdateUserRequest;
import com.verdant.salon_ecomm.dtos.user.UserDto;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.nio.file.attribute.UserPrincipal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class UserGrapQLController {

    private final UserService userService;

    @QueryMapping
    public UserDto.Profile me(@AuthenticationPrincipal User principal) {
        return userService.getUserById(principal.getId());
    }

    @MutationMapping
    public UserDto.Profile updateProfile(
        @Argument UpdateUserRequest input,
        @AuthenticationPrincipal User principal
    ) {
        return userService.updateUserProfile(principal.getId(), input);
    }

}
