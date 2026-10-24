package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.user.*;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.response.AuthResult;
import com.verdant.salon_ecomm.services.AuthenticationService;
import com.verdant.salon_ecomm.services.PublicFormRateLimiter;
import com.verdant.salon_ecomm.services.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Locale;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;

    private static final String COOKIE_NAME_REFRESH_TOKEN = "refreshToken";
    private static final String SAME_SITE_LAX = "Lax";

    @Value("${app.production}")
    private boolean isProduction;

    private final PublicFormRateLimiter rateLimiter;

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
    public ResponseEntity<AuthResult> logIn(
        @Valid @RequestBody LogInUserDto logInUser,
        HttpServletRequest request
    ) {
        String ip = request.getRemoteAddr();
        rateLimiter.check("login:ip:" + ip, 10, Duration.ofMinutes(15));
        rateLimiter.check(
            "login:email:" + logInUser.getEmail().toLowerCase(Locale.ROOT).trim(),
            3,
            Duration.ofMinutes(15)
        );

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
    public ResponseEntity<?> verifyUser(
        @Valid @RequestBody VerifyUserDto verifyUser,
        HttpServletRequest request
    ) {
        String ip = request.getRemoteAddr();
        rateLimiter.check("verify:ip:" + ip, 10, Duration.ofMinutes(15));
        rateLimiter.check(
            "verify:email:" + verifyUser.getEmail().toLowerCase(Locale.ROOT).trim(),
            3,
            Duration.ofMinutes(15)
        );

        authenticationService.verifyUser(verifyUser);
        return ResponseEntity.ok("Account verified");
    }

    @PostMapping("/resend")
    public ResponseEntity<?> resendVerificationCode(@Valid @RequestBody ResendVerificationCodeDto request) {
        authenticationService.resendVerificationCode(request.getEmail());
        return ResponseEntity.ok("Verification code resent");
    }

    // "Forgot password" step 1 — request a reset code. Deliberately returns
    // the same 200 response whether the email exists, so this can't be
    // used to enumerate registered accounts.
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request) {
        authenticationService.forgotPassword(request.getEmail());
        return ResponseEntity.ok("If an account with that email exists, a reset code has been sent.");
    }

    // "Forgot password" step 2 — submit the code + new password together.
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
        @Valid @RequestBody ResetPasswordDto request,
        HttpServletRequest requests
    ) {
        String ip = requests.getRemoteAddr();
        rateLimiter.check("reset:ip:" + ip, 3, Duration.ofMinutes(15));
        rateLimiter.check(
            "reset:email:" + request.getEmail().toLowerCase(Locale.ROOT).trim(),
            3,
            Duration.ofMinutes(15)
        );

        authenticationService.resetPassword(request);
        return ResponseEntity.ok("Password has been reset successfully.");
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
