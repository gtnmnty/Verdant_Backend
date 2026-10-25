package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.user.*;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.security.RefreshTokenCookies;
import com.verdant.salon_ecomm.services.RefreshTokenService;
import com.verdant.salon_ecomm.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RequestMapping("v1/users")
@RestController
public class UserRESTController {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookies refreshTokenCookies;

    @PutMapping("/update-profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto.Profile> updateProfile(
        @AuthenticationPrincipal User principal,
        @Valid @RequestBody UpdateUserRequest request
    ){
        UserDto.Profile user = userService.updateUserProfile(principal.getId(), request);
        return ResponseEntity.ok(user);
    }

    @PostMapping(value = "/avatar", consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto.Profile> updateAvatar(
        @AuthenticationPrincipal User principal,
        @RequestParam("file") MultipartFile file
    ){
        UserDto.Profile user = userService.updateAvatar(principal.getId(), file);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(
        @AuthenticationPrincipal User principal,
        @Valid @RequestBody ChangePasswordRequest request
    ){
        // M-2 flow: updateUserPassword() changes the hash and deletes ALL refresh tokens (commits),
        // then this device gets a new refresh cookie. Every other session dies at its next refresh;
        // the user who changed the password is not bounced to the login page.
        userService.updateUserPassword(principal.getId(), request);
        String refreshToken = refreshTokenService.createRefreshToken(principal.getEmail());
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.issue(refreshToken).toString())
            .build();
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal User principal){
        userService.deleteUserById(principal.getId());
        return ResponseEntity.noContent().build();
    }
}
