package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.user.LogInUserDto;
import com.verdant.salon_ecomm.dtos.user.RegisterUserDto;
import com.verdant.salon_ecomm.dtos.user.ResetPasswordDto;
import com.verdant.salon_ecomm.dtos.user.VerifyResetCodeDto;
import com.verdant.salon_ecomm.dtos.user.VerifyUserDto;
import com.verdant.salon_ecomm.dtos.user.events.UserPasswordChangedEvent;
import com.verdant.salon_ecomm.dtos.user.events.UserPasswordResetCodeRequestedEvent;
import com.verdant.salon_ecomm.exceptions.*;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.response.AuthResult;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final RefreshTokenService refreshTokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final LoginThrottle loginThrottle;

    // M-4: BCrypt hash of a random value, compared against when the email is unknown. Without it an
    // unknown email answers in ~1 ms and a known one in ~100 ms (one BCrypt round), and that timing
    // gap would reveal which emails have accounts even though both now return the same 401.
    private String timingDummyHash;

    @PostConstruct
    void initTimingDummyHash() {
        timingDummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // M-10 flow: duplicate check -> build user + hashed code -> saveAndFlush (DB constraints checked)
    // -> send the code -> commit. The email used to go out BEFORE save(), so input the DB rejected
    // (e.g. a null name) still mailed a code and then failed with a 500.
    // Why the email is sent inside the transaction and not after commit: if SMTP fails, the exception
    // rolls the signup back and the 503 "couldn't send this email" is true, so the user can simply
    // retry. After commit, the same failure would leave an account with no code plus an error for a
    // signup that did succeed, and the retry would answer "email already exists".
    // Remaining gap: the commit failing after the email went out (rare) leaves a code for no account.
    @Transactional
    public User signUp(RegisterUserDto input) {
        // First, so no code is generated or mailed for an address that is already taken.
        if (userRepository.existsByEmail(input.getEmail())) {
            throw new DuplicateEmailException("Account with this email already exists!");
        }

        User user = new User();
        user.setFullName(input.getFullName().trim());
        user.setEmail(input.getEmail());
        user.setPhone(input.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(input.getPassword()));
        user.setRole(AccountRole.CUSTOMER);
        String plainCode = issueVerificationCode(user, 5);
        user.setEnabled(false);

        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two signups for the same address at once: the unique index catches the second one.
            throw new DuplicateEmailException("Account with this email already exists!");
        }

        sendVerificationEmail(saved, plainCode);
        return saved;
    }

    // M-4 flow: throttle check -> password check (dummy hash for unknown emails) -> only then account
    // status. Before, an unknown email returned 404 and an unverified account returned 403 without the
    // password being checked, so anyone could learn which emails are registered and verified.
    // Now every wrong email/password pair gets the same 401 "Invalid email or password." Status
    // errors (403 not verified / suspended) are only shown to someone who knows the password.
    // The password is checked here rather than through AuthenticationManager because
    // DaoAuthenticationProvider runs its enabled/locked checks BEFORE the password check.
    public AuthResult authenticate(LogInUserDto input) throws InvalidCredentialsException {
        loginThrottle.checkAllowed(input.getEmail());

        User user = userRepository.findByEmail(input.getEmail()).orElse(null);
        String hashToCheck = user != null ? user.getPasswordHash() : timingDummyHash;
        boolean passwordMatches = passwordEncoder.matches(input.getPassword(), hashToCheck);

        if (user == null || !passwordMatches) {
            loginThrottle.recordFailure(input.getEmail());
            throw new InvalidCredentialsException("Invalid credentials.");
        }
        loginThrottle.reset(input.getEmail());

        if (!user.isAccountNonLocked()) throw new ForbiddenException("Account is suspended.");

        if (!user.isEnabled()) {
            throw new AccountNotVerifiedException("Account is not verified. Please check your email.");
        }

        String accessToken = jwtService.generateToken(user);

        // M-37: a new session for this device; the user's other sessions stay signed in. Only the raw value
        // goes to the cookie; the database keeps its hash.
        String refreshTokenString = refreshTokenService.createRefreshToken(input.getEmail());

        long expiresInMs = jwtService.getExpirationTime();

        return new AuthResult(accessToken, refreshTokenString, expiresInMs);
    }

    public void verifyUser(VerifyUserDto input) {
        User user = userRepository.findByEmail(input.getEmail())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isEmailVerified()) {
            throw BusinessRuleException.conflict("Account is already verified.");
        }

        if (user.getVerificationCode() == null || user.getVerificationCode().isBlank()) {
            throw new InvalidVerificationCodeException("Invalid verification code.");
        }

        // Stored as a BCrypt hash, like the forgot-password code.
        if (input.getVerificationCode() == null
            || !passwordEncoder.matches(input.getVerificationCode(), user.getVerificationCode())) {
            throw new InvalidVerificationCodeException("Invalid verification code.");
        }

        if (user.getVerificationCodeExpiration() == null
            || user.getVerificationCodeExpiration().isBefore(OffsetDateTime.now())) {
            throw new VerificationCodeExpiredException("Verification code has expired.");
        }

        user.setEnabled(true);
        if (user.getStatus() == AccountStatus.UNVERIFIED) user.setStatus(AccountStatus.ACTIVE);
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

            String plainCode = issueVerificationCode(user, 5);
            sendVerificationEmail(user, plainCode);
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
    // @Transactional so the mail listener (AFTER_COMMIT, @Async) fires once the code is saved; the
    // response no longer waits on SMTP.
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email)
            .filter(User::isEmailVerified)
            .filter(User::isAccountNonLocked)
            .ifPresent(user -> {
            String plainCode = generateVerificationCode();
            // BCrypt via the existing passwordEncoder bean, same as
            // real passwords — salted per-call, deliberately slow.
            user.setResetPasswordCode(passwordEncoder.encode(plainCode));
            user.setResetPasswordCodeExpiration(OffsetDateTime.now().plusMinutes(10));
            userRepository.save(user);
            eventPublisher.publishEvent(
                new UserPasswordResetCodeRequestedEvent(user.getId(), user.getEmail(), plainCode));
        });
    }

    // Optional check between steps 1 and 2: lets the UI confirm the emailed code
    // before asking for a new password. Read-only — the code stays valid so
    // resetPassword() can redeem it; same message as resetPassword() on failure.
    public void verifyResetCode(VerifyResetCodeDto input) {
        boolean codeValid = userRepository.findByEmail(input.getEmail())
            .map(user -> isResetCodeValid(user, input.getCode()))
            .orElse(false);

        if (!codeValid) {
            throw new InvalidVerificationCodeException("Invalid or expired code.");
        }
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

        if (!isResetCodeValid(user, input.getCode())) {
            throw new InvalidVerificationCodeException("Invalid or expired code.");
        }

        user.setPasswordHash(passwordEncoder.encode(input.getNewPassword()));
        // Single-use: clear the code so it can't be redeemed twice.
        user.setResetPasswordCode(null);
        user.setResetPasswordCodeExpiration(null);
        userRepository.save(user);

        refreshTokenService.deleteByUserId(user.getId());
        eventPublisher.publishEvent(new UserPasswordChangedEvent(user));
    }

    private boolean isResetCodeValid(User user, String code) {
        return user.getResetPasswordCode() != null
            && user.getResetPasswordCodeExpiration() != null
            && user.getResetPasswordCodeExpiration().isAfter(OffsetDateTime.now())
            && passwordEncoder.matches(code, user.getResetPasswordCode());
    }

    private void sendVerificationEmail(User user, String plainCode) {

        try {
            emailService.sendVerificationEmail(user.getEmail(), plainCode);
        } catch (MessagingException e) {
            throw new EmailDeliveryException("Failed to send verification email to: " + user.getEmail(), e);
        }
    }

    private String generateVerificationCode() {
        int code = SECURE_RANDOM.nextInt(900000) + 100000;
        return String.valueOf(code);
    }

    // Stores only the BCrypt hash on the user and returns the plain code for the email.
    private String issueVerificationCode(User user, int ttlMinutes) {
        String plainCode = generateVerificationCode();
        user.setVerificationCode(passwordEncoder.encode(plainCode));
        user.setVerificationCodeExpiration(OffsetDateTime.now().plusMinutes(ttlMinutes));
        return plainCode;
    }

    public AuthResult refresh(String refreshToken) {
        return refreshTokenService.rotateRefreshToken(refreshToken);
    }

    // M-37: ends only the session this cookie belongs to; the user's other devices stay signed in.
    public void logout(String refreshToken) {
        refreshTokenService.revokeSession(refreshToken);
    }

    // Lifetime of a code mailed to a new address (staff change here, self-service change in UserService).
    // Shared so the email text and the real expiry can't drift apart.
    public static final int EMAIL_CHANGE_CODE_TTL_MINUTES = 15;

    // Returns the plain code so the caller can mail it; the user only keeps the hash.
    public String prepareVerification(User user) {
        return issueVerificationCode(user, EMAIL_CHANGE_CODE_TTL_MINUTES);
    }
}