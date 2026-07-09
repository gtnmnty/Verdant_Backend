package com.verdant.salon_ecomm.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verdant.salon_ecomm.dtos.user.LogInUserDto;
import com.verdant.salon_ecomm.dtos.user.RegisterUserDto;
import com.verdant.salon_ecomm.dtos.user.ResendVerificationCodeDto;
import com.verdant.salon_ecomm.dtos.user.UserDto;
import com.verdant.salon_ecomm.dtos.user.VerifyUserDto;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.response.AuthResult;
import com.verdant.salon_ecomm.services.AuthenticationService;
import com.verdant.salon_ecomm.services.RefreshTokenService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;

    private static final String COOKIE_NAME_REFRESH_TOKEN = "refreshToken";
    //private static final String SAME_SITE_STRICT = "Strict";
    private static final String SAME_SITE_LAX = "Lax";

    @Value("${app.production}")
    private boolean isProduction;
    

    @PostMapping("/signup")
    public ResponseEntity<UserDto.Summary> signUp(@Valid @RequestBody RegisterUserDto registerUser) {
        User registered = authenticationService.signUp(registerUser);
        return ResponseEntity.ok(new UserDto.Summary(
                registered.getId(),
                registered.getFullName(),
                registered.getEmail(),
                registered.getPhone()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResult> logIn(@Valid @RequestBody LogInUserDto logInUser) {
        AuthResult authResult = authenticationService.authenticate(logInUser);
        String refreshToken = authResult.getRefreshToken();

        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME_REFRESH_TOKEN, refreshToken)
                .httpOnly(true)
                .secure(isProduction)
                .path("/")
                .maxAge(refreshTokenService.getRefreshTokenDurationSeconds())
                .sameSite(SAME_SITE_LAX)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(authResult);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResult> refresh(@CookieValue(name = COOKIE_NAME_REFRESH_TOKEN) String refreshToken) {
        AuthResult authResult = authenticationService.refresh(refreshToken);
        String refreshToken1 = authResult.getRefreshToken();

        ResponseCookie newCookie = ResponseCookie.from(COOKIE_NAME_REFRESH_TOKEN, refreshToken1)
                .httpOnly(true)
                .secure(isProduction)
                .path("/")
                .maxAge(refreshTokenService.getRefreshTokenDurationSeconds())
                .sameSite(SAME_SITE_LAX)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newCookie.toString())
                .body(authResult);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyUser(@Valid @RequestBody VerifyUserDto verifyUser) {
        authenticationService.verifyUser(verifyUser);
        return ResponseEntity.ok("Account verified");
    }

    @PostMapping("/resend")
    public ResponseEntity<?> resendVerificationCode(@Valid @RequestBody ResendVerificationCodeDto request) {
        authenticationService.resendVerificationCode(request.getEmail());
        return ResponseEntity.ok("Verification code resent");
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logoutUser(@CookieValue(name = COOKIE_NAME_REFRESH_TOKEN) String refreshToken) {
        authenticationService.logout(refreshToken);

        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME_REFRESH_TOKEN, "")
                .httpOnly(true)
                .secure(isProduction)
                .path("/")
                .maxAge(0)
                .sameSite(SAME_SITE_LAX)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
