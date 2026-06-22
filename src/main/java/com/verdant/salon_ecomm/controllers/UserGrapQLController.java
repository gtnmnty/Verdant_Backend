package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.user.UpdateUserRequest;
import com.verdant.salon_ecomm.dtos.user.UserResponse;
import com.verdant.salon_ecomm.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class UserGrapQLController {

    private final UserService userService;

    @QueryMapping
    public UserResponse.Profile me(Authentication authentication) {
        UUID id = UUID.fromString(authentication.getName());
        return userService.getUserById(id);
    }

    @MutationMapping
    public UserResponse.Profile updateProfile(
            @Argument UpdateUserRequest input,
            Authentication authentication
    ) {
        UUID id = UUID.fromString(authentication.getName());
        return userService.updateUserProfile(id, input);
    }

}
