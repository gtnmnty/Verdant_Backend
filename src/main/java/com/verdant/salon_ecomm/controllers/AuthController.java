package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.user.*;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.response.AuthResult;
import com.verdant.salon_ecomm.security.RefreshTokenCookies;
import com.verdant.salon_ecomm.services.AuthenticationService;
import com.verdant.salon_ecomm.services.PublicFormRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    private final RefreshTokenCookies refreshTokenCookies;
    private final PublicFormRateLimiter rateLimiter;

    private static final Duration WINDOW = Duration.ofMinutes(15);

    // M-5: every endpoint here that sends an email is limited per IP and per address, so nobody can
    // make the app mail an inbox over and over (email bombing) or burn the SMTP quota.
    // The per-address limit is what stops one IP from targeting one inbox; the per-IP limit stops one
    // client from cycling through many addresses. @Valid runs first, so the email is never null here.
    @PostMapping("/signup")
    public ResponseEntity<UserDto.Summary> signUp(
        @Valid @RequestBody RegisterUserDto registerUser,
        HttpServletRequest request
    ) {
        rateLimiter.check("signup:ip:" + request.getRemoteAddr(), 5, Duration.ofHours(1));
        rateLimiter.check("signup:email:" + normalize(registerUser.getEmail()), 3, WINDOW);

        User registered = authenticationService.signUp(registerUser);
        return ResponseEntity.ok(new UserDto.Summary(
                registered.getId(),
                registered.getFullName(),
                registered.getEmail(),
                registered.getPhone()
        ));
    }

    // M-5: only a per-IP cap here. The old "3 tries per email per 15 minutes" let anyone lock a
    // chosen user out for 15 minutes with three bad passwords. Per-account protection is now the
    // short, growing delay in LoginThrottle (called from AuthenticationService.authenticate).
    @PostMapping("/login")
    public ResponseEntity<AuthResult> logIn(
        @Valid @RequestBody LogInUserDto logInUser,
        HttpServletRequest request
    ) {
        rateLimiter.check("login:ip:" + request.getRemoteAddr(), 10, WINDOW);

        AuthResult authResult = authenticationService.authenticate(logInUser);
        ResponseCookie cookie = refreshTokenCookies.issue(authResult.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(authResult);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResult> refresh(@CookieValue(name = RefreshTokenCookies.COOKIE_NAME) String refreshToken) {
        AuthResult authResult = authenticationService.refresh(refreshToken);
        ResponseCookie newCookie = refreshTokenCookies.issue(authResult.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newCookie.toString())
                .body(authResult);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyUser(
        @Valid @RequestBody VerifyUserDto verifyUser,
        HttpServletRequest request
    ) {
        rateLimiter.check("verify:ip:" + request.getRemoteAddr(), 10, WINDOW);
        rateLimiter.check("verify:email:" + normalize(verifyUser.getEmail()), 3, WINDOW);

        authenticationService.verifyUser(verifyUser);
        return ResponseEntity.ok("Account verified");
    }

    @PostMapping("/resend")
    public ResponseEntity<?> resendVerificationCode(
        @Valid @RequestBody ResendVerificationCodeDto resend,
        HttpServletRequest request
    ) {
        rateLimiter.check("resend:ip:" + request.getRemoteAddr(), 10, WINDOW);
        rateLimiter.check("resend:email:" + normalize(resend.getEmail()), 3, WINDOW);

        authenticationService.resendVerificationCode(resend.getEmail());
        return ResponseEntity.ok("Verification code resent");
    }

    // "Forgot password" step 1 — request a reset code. Deliberately returns
    // the same 200 response whether the email exists, so this can't be
    // used to enumerate registered accounts. The limits apply to unknown
    // addresses too, so a 429 doesn't reveal anything either.
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
        @Valid @RequestBody ForgotPasswordRequestDto forgot,
        HttpServletRequest request
    ) {
        rateLimiter.check("forgot:ip:" + request.getRemoteAddr(), 10, WINDOW);
        rateLimiter.check("forgot:email:" + normalize(forgot.getEmail()), 3, WINDOW);

        authenticationService.forgotPassword(forgot.getEmail());
        return ResponseEntity.ok("If an account with that email exists, a reset code has been sent.");
    }

    // "Forgot password" step 2 — submit the code + new password together.
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
        @Valid @RequestBody ResetPasswordDto reset,
        HttpServletRequest request
    ) {
        rateLimiter.check("reset:ip:" + request.getRemoteAddr(), 3, WINDOW);
        rateLimiter.check("reset:email:" + normalize(reset.getEmail()), 3, WINDOW);

        authenticationService.resetPassword(reset);
        return ResponseEntity.ok("Password has been reset successfully.");
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logoutUser(@CookieValue(name = RefreshTokenCookies.COOKIE_NAME) String refreshToken) {
        authenticationService.logout(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookies.clear().toString())
                .build();
    }

    // Same key for "Foo@x.com " and "foo@x.com", so case or spaces can't dodge the per-address limit.
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
