package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.user.LogInUserDto;
import com.verdant.salon_ecomm.dtos.user.RegisterUserDto;
import com.verdant.salon_ecomm.dtos.user.VerifyUserDto;
import com.verdant.salon_ecomm.entities.RefreshToken;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.exceptions.*;
import com.verdant.salon_ecomm.models.enums.AccountRole;
import com.verdant.salon_ecomm.models.enums.AccountStatus;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.response.AuthResult;
import jakarta.mail.MessagingException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final JwtService jwtService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final RefreshTokenService refreshTokenService;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            EmailService emailService, JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

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

    public AuthResult authenticate(LogInUserDto input) throws InvalidCrendetialsException {
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
            throw new InvalidCrendetialsException("Invalid credentials.");
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

        if (!user.getVerificationCode().equals(input.getVerificationCode())) {
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
