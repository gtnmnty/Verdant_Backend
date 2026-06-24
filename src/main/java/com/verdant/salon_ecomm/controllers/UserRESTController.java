package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.user.*;
import com.verdant.salon_ecomm.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RequiredArgsConstructor
@RequestMapping("v1/users")
@RestController
public class UserRESTController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserDto.Summary> createUser(
        @Valid @RequestBody RegisterUserDto request,
        UriComponentsBuilder uriBuilder
    ) {
        // 1. Pass the 'request' payload object into your service folder
        UserDto.Summary newUser = userService.registerUser(request);

        // 2. Build the Location URI using the inner record's .id() accessor
        var uri = uriBuilder.path("/api/v1/users/{id}").buildAndExpand(newUser.id()).toUri();

        return ResponseEntity.created(uri).body(newUser);
    }

    @PutMapping("/{id}/update-profile")
    public ResponseEntity<UserDto.Profile> updateProfile(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateUserRequest request
    ){
        UserDto.Profile user = userService.updateUserProfile(id, request);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}/change-password")
    public ResponseEntity<Void> changePasswordByID(
            @PathVariable UUID id,
            @Valid @RequestBody ChangePasswordRequest request
    ){
        userService.updateUserPassword(id, request);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<UserDto> deleteUser(@PathVariable UUID id){
        userService.deleteUserById(id);
        return ResponseEntity.noContent().build();
    }
}
