package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.user.LogInUserDto;
import com.verdant.salon_ecomm.dtos.user.RegisterUserDto;
import com.verdant.salon_ecomm.dtos.user.VerifyUserDto;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.response.AuthResponse;
import com.verdant.salon_ecomm.services.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationService authenticationService;

    @PostMapping("/signup")
    public ResponseEntity<User> signUp(@RequestBody RegisterUserDto registerUser) {
        User registered = authenticationService.signUp(registerUser);
        return ResponseEntity.ok(registered);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> logIn(@RequestBody LogInUserDto logInUser) {
        AuthResponse authResponse = authenticationService.authenticate(logInUser);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = "refreshToken") String refreshToken) {
        AuthResponse response = authenticationService.refresh(refreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyUser(@RequestBody VerifyUserDto verifyUser) {
        authenticationService.verifyUser(verifyUser);
        return ResponseEntity.ok("Account verified");
    }

    @PostMapping("/resend")
    public ResponseEntity<?> resendVerificationCode(@RequestBody String email) {
        authenticationService.resendVerifictionCode(email);
        return ResponseEntity.ok("Verification code resent");
    }
}
