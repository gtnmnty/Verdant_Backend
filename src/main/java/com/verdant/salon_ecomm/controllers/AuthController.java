package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.user.LogInUserDto;
import com.verdant.salon_ecomm.dtos.user.RegisterUserDto;
import com.verdant.salon_ecomm.dtos.user.ResendVerificationCodeDto;
import com.verdant.salon_ecomm.dtos.user.VerifyUserDto;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.response.AuthResult;
import com.verdant.salon_ecomm.services.AuthenticationService;
import com.verdant.salon_ecomm.services.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.production}")
    private boolean isProduction;

    @PostMapping("/signup")
    public ResponseEntity<User> signUp(@RequestBody RegisterUserDto registerUser) {
        User registered = authenticationService.signUp(registerUser);
        return ResponseEntity.ok(registered);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResult> logIn(@RequestBody LogInUserDto logInUser) {
        AuthResult authResult = authenticationService.authenticate(logInUser);
        String refreshToken = authResult.getRefreshToken();

        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(isProduction)
                .path("/")
                .maxAge(refreshTokenService.getRefreshTokenDurationSeconds())
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(authResult);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResult> refresh(@CookieValue(name = "refreshToken") String refreshToken) {
        AuthResult authResult = authenticationService.refresh(refreshToken);
        String refreshToken1 = authResult.getRefreshToken();

        ResponseCookie newCookie = ResponseCookie.from("refreshToken", refreshToken1)
                .httpOnly(true)
                .secure(isProduction)
                .path("/")
                .maxAge(refreshTokenService.getRefreshTokenDurationSeconds())
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newCookie.toString())
                .body(authResult);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyUser(@RequestBody VerifyUserDto verifyUser) {
        authenticationService.verifyUser(verifyUser);
        return ResponseEntity.ok("Account verified");
    }

    @PostMapping("/resend")
    public ResponseEntity<?> resendVerificationCode(@RequestBody ResendVerificationCodeDto request) {
        authenticationService.resendVerificationCode(request.getEmail());
        return ResponseEntity.ok("Verification code resent");
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logoutUser(@CookieValue(name = "refreshToken") String refreshToken) {
        authenticationService.logout(refreshToken);

        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(isProduction)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
