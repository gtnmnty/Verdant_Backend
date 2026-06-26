package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.user.LogInUserDto;
import com.verdant.salon_ecomm.dtos.user.RegisterUserDto;
import com.verdant.salon_ecomm.dtos.user.VerifyUserDto;
import com.verdant.salon_ecomm.entities.RefreshToken;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.response.AuthResponse;
import com.verdant.salon_ecomm.services.AuthenticationService;
import com.verdant.salon_ecomm.services.JwtService;
import com.verdant.salon_ecomm.services.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final JwtService jwtService;
    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;

    /**
     * Registers a new user account.
     *
     * @param  registerUser  the registration details
     * @return               the created user
     */
    @PostMapping("/signup")
    public ResponseEntity<User> signUp(@RequestBody RegisterUserDto registerUser) {
        User registered = authenticationService.signUp(registerUser);
        return ResponseEntity.ok(registered);
    }

    /**
     * Authenticates a user and returns their authentication details.
     *
     * @param logInUser the login credentials to authenticate
     * @return the authentication response for the supplied credentials
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> logIn(@RequestBody LogInUserDto logInUser) {
        AuthResponse authResponse = authenticationService.authenticate(logInUser);
        return ResponseEntity.ok(authResponse);
    }

    /**
     * Verifies a user account.
     *
     * @param  verifyUser  the verification details
     * @return             a response with "Account verified" on success, or a bad request with the error message if verification fails
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyUser(@RequestBody VerifyUserDto verifyUser) {
        try {
            authenticationService.verifyUser(verifyUser);
            return ResponseEntity.ok("Account verified");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Resends a verification code to the specified email address.
     *
     * @param email the email address to send the verification code to
     * @return a success message when the code is resent, or an error message when resending fails
     */
    @PostMapping("/resend")
    public ResponseEntity<?> resendVerificationCode(@RequestBody String email){
        try{
            authenticationService.resendVerifictionCode(email);
            return ResponseEntity.ok("Verification code resent");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
