package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.user.*;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
        UserDto.Summary newUser = userService.registerUser(request);

        var uri = uriBuilder.path("/api/v1/users/{id}").buildAndExpand(newUser.id()).toUri();

        return ResponseEntity.created(uri).body(newUser);
    }

    @PutMapping("/update-profile")
    public ResponseEntity<UserDto.Profile> updateProfile(
        @AuthenticationPrincipal User principal,
        @Valid @RequestBody UpdateUserRequest request
    ){
        UserDto.Profile user = userService.updateUserProfile(principal.getId(), request);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(
        @AuthenticationPrincipal User principal,
        @Valid @RequestBody ChangePasswordRequest request
    ){
        userService.updateUserPassword(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<UserDto> deleteAccount(@AuthenticationPrincipal User principal){
        userService.deleteUserById(principal.getId());
        return ResponseEntity.noContent().build();
    }
}
