package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.user.LogInUserDto;
import com.verdant.salon_ecomm.dtos.user.RegisterUserDto;
import com.verdant.salon_ecomm.dtos.user.VerifyUserDto;
import com.verdant.salon_ecomm.entities.RefreshToken;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.exceptions.AccountNotVerifiedException;
import com.verdant.salon_ecomm.exceptions.InvalidVerificationCodeException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.exceptions.VerificationCodeExpiredException;
import com.verdant.salon_ecomm.repositories.RefreshTokenRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.response.AuthResponse;
import jakarta.mail.MessagingException;
import org.springframework.security.authentication.AuthenticationManager;
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
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Creates an authentication service with its required dependencies.
     */
    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            EmailService emailService, JwtService jwtService,
            RefreshTokenService refreshTokenService, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Registers a new user account and sends a verification code.
     *
     * @param  input  the registration details to copy into the new user
     * @return        the saved user
     */
    public User signUp(RegisterUserDto input) {
        User user = new User();
        user.setFullName(input.getFullName());
        user.setEmail(input.getEmail());
        user.setPhone(input.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(input.getPassword()));
        user.setVerificationCode(generateVerificationCode());
        user.setVerificationCodeExpiration(OffsetDateTime.now().plusMinutes(5));
        user.setEnabled(false);

        sendVerificationEmail(user);
        return userRepository.save(user);
    }

    /**
     * Authenticates a user and issues access and refresh tokens.
     *
     * @param input the login credentials
     * @return an authentication response containing the access token, refresh token, and access token expiration time
     * @throws ResourceNotFoundException if no user exists for the provided email
     * @throws AccountNotVerifiedException if the user's account is not verified
     */
    public AuthResponse authenticate(LogInUserDto input) {
        User user = userRepository.findByEmail(input.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isEnabled()) {
            throw new AccountNotVerifiedException("Account is not verified. Please check your email.");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        input.getEmail(),
                        input.getPassword()
                )
        );

        String accessToken = jwtService.generateToken(user);

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(input.getEmail());
        String refreshTokenString = refreshToken.getToken();

        long expiresAt = jwtService.getExpirationTime();

        return new AuthResponse(accessToken, refreshTokenString, expiresAt);
    }

    /**
     * Verifies a user's account with the provided code.
     *
     * @param input the email address and verification code to validate
     * @throws ResourceNotFoundException if no user exists for the supplied email
     * @throws VerificationCodeExpiredException if the stored verification code has expired
     * @throws InvalidVerificationCodeException if the provided code does not match the stored code
     */
    public void verifyUser(VerifyUserDto input) {
        User user = userRepository.findByEmail(input.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getVerificationCodeExpiration().isBefore(OffsetDateTime.now())) {
            throw new VerificationCodeExpiredException("Verification code has expired.");
        }

        if (!user.getVerificationCode().equals(input.getVerificationCode())) {
            throw new InvalidVerificationCodeException("Invalid verification code.");
        }

        user.setEnabled(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiration(null);
        userRepository.save(user);
    }

    public void resendVerifictionCode(String email){
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if(optionalUser.isPresent()){
            User user = optionalUser.get();
            if(user.isEnabled()){
                throw new RuntimeException("Already verified");
            }

            user.setVerificationCode(generateVerificationCode());
            user.setVerificationCodeExpiration(OffsetDateTime.now().plusMinutes(5));
            sendVerificationEmail(user);
            userRepository.save(user);
        } else {
            throw new RuntimeException("User not found");
        }
    }

    public void sendVerificationEmail(User user) {

        try {
            emailService.sendVerificationEmail(user.getEmail(), user.getVerificationCode());
        } catch (MessagingException e) {
            e.printStackTrace();
            // Handles the message error if it didn't work
        }
    }

    private String generateVerificationCode() {
        int code = SECURE_RANDOM.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    public AuthResponse refresh(String refreshToken) {
        return refreshTokenService.rotateRefreshToken(refreshToken);
    }
}
