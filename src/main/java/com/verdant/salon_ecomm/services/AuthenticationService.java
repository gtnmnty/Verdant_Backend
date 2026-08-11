package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.user.LogInUserDto;
import com.verdant.salon_ecomm.dtos.user.RegisterUserDto;
import com.verdant.salon_ecomm.dtos.user.ResetPasswordDto;
import com.verdant.salon_ecomm.dtos.user.VerifyUserDto;
import com.verdant.salon_ecomm.entities.RefreshToken;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.exceptions.*;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.response.AuthResult;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final JwtService jwtService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final RefreshTokenService refreshTokenService;

    public User signUp(RegisterUserDto input) {

        User user = new User();
        user.setFullName(input.getFullName());
        user.setEmail(input.getEmail());
        user.setPhone(input.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(input.getPassword()));
        user.setRole(AccountRole.CUSTOMER);
        user.setVerificationCode(generateVerificationCode());
        user.setVerificationCodeExpiration(OffsetDateTime.now().plusMinutes(5));
        user.setEnabled(false);

        if(userRepository.existsByEmail(input.getEmail())) {
            throw new DuplicateEmailException("Account with this email already exists!");
        }

        sendVerificationEmail(user);
        return userRepository.save(user);
    }

    public AuthResult authenticate(LogInUserDto input) throws InvalidCredentialsException {
        User user = userRepository.findByEmail(input.getEmail())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isEnabled()) {
            throw new AccountNotVerifiedException("Account is not verified. Please check your email.");
        }

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    input.getEmail(),
                    input.getPassword()
                )
            );
        } catch (BadCredentialsException e) {
            throw new InvalidCredentialsException("Invalid credentials.");
        }

        String accessToken = jwtService.generateToken(user);

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(input.getEmail());
        String refreshTokenString = refreshToken.getToken();

        long expiresAt = jwtService.getExpirationTime();

        return new AuthResult(accessToken, refreshTokenString, expiresAt);
    }

    public void verifyUser(VerifyUserDto input) {
        User user = userRepository.findByEmail(input.getEmail())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isEmailVerified()) {
            throw new IllegalStateException("Account is already verified.");
        }

        if (user.getVerificationCode() == null || user.getVerificationCode().isBlank()) {
            throw new InvalidVerificationCodeException("Invalid verification code.");
        }

        if (!Objects.equals(input.getVerificationCode(), user.getVerificationCode())) {
            throw new InvalidVerificationCodeException("Invalid verification code.");
        }

        if (user.getVerificationCodeExpiration().isBefore(OffsetDateTime.now())) {
            throw new VerificationCodeExpiredException("Verification code has expired.");
        }

        user.setEnabled(true);
        user.setStatus(AccountStatus.ACTIVE);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiration(null);
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(OffsetDateTime.now());
        userRepository.save(user);
    }

    public void resendVerificationCode(String email){
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if(optionalUser.isPresent()){
            User user = optionalUser.get();
            if(user.isEnabled()){
                throw new AccountAlreadyVerifiedException("Already verified");
            }

            user.setVerificationCode(generateVerificationCode());
            user.setVerificationCodeExpiration(OffsetDateTime.now().plusMinutes(5));
            sendVerificationEmail(user);
            userRepository.save(user);
        } else {
            throw new ResourceNotFoundException("User not found");
        }
    }

    // Step 1 of forgot-password: issue a short-lived numeric code and email
    // it, mirroring the signup verification-code pattern. Deliberately does
    // NOT throw when the email isn't found — returning the same response
    // either way stops an attacker from using this endpoint to discover
    // which emails have accounts.
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String plainCode = generateVerificationCode();
            // CHANGED: BCrypt via the existing passwordEncoder bean, same as
            // real passwords — salted per-call, deliberately slow.
            user.setResetPasswordCode(passwordEncoder.encode(plainCode));
            user.setResetPasswordCodeExpiration(OffsetDateTime.now().plusMinutes(10));
            userRepository.save(user);
            sendPasswordResetEmail(user, plainCode);
        });
    }

    // Step 2 of forgot-password: verify the code and set the new password in
    // one call. Unlike the admin-triggered link flow (PasswordResetTokenService),
    // this is code-based so a customer can request AND redeem it themselves,
    // with no admin in the loop.
    @Transactional
    public void resetPassword(ResetPasswordDto input) {
        User user = userRepository.findByEmailForUpdate(input.getEmail())
            .orElseThrow(() -> new InvalidVerificationCodeException("Invalid or expired code."));
        // NOTE: same error/message on "email not found" as on "wrong code" —
        // matches forgotPassword()'s no-account-enumeration behavior.

        boolean codeValid = user.getResetPasswordCode() != null
            && user.getResetPasswordCodeExpiration() != null
            && user.getResetPasswordCodeExpiration().isAfter(OffsetDateTime.now())
            && passwordEncoder.matches(input.getCode(), user.getResetPasswordCode());

        if (!codeValid) {
            throw new InvalidVerificationCodeException("Invalid or expired code.");
        }

        user.setPasswordHash(passwordEncoder.encode(input.getNewPassword()));
        // Single-use: clear the code so it can't be redeemed twice.
        user.setResetPasswordCode(null);
        user.setResetPasswordCodeExpiration(null);
        userRepository.save(user);

        refreshTokenService.deleteByUserId(user.getId());
    }

    private void sendPasswordResetEmail(User user, String plainCode) {
        try {
            emailService.sendPasswordResetCodeEmail(user.getEmail(), plainCode);
        } catch (MessagingException e) {
            throw new EmailDeliveryException("Failed to send password reset email to: " + user.getEmail(), e);
        }
    }

    public void sendVerificationEmail(User user) {

        try {
            emailService.sendVerificationEmail(user.getEmail(), user.getVerificationCode());
        } catch (MessagingException e) {
            throw new EmailDeliveryException("Failed to send verification email to: " + user.getEmail(), e);
        }
    }

    private String generateVerificationCode() {
        int code = SECURE_RANDOM.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    public AuthResult refresh(String refreshToken) {
        return refreshTokenService.rotateRefreshToken(refreshToken);
    }

    public void logout(String refreshToken) {
        RefreshToken token = refreshTokenService.findByToken(refreshToken);
        refreshTokenService.deleteByUserId(token.getUser().getId());
    }
}