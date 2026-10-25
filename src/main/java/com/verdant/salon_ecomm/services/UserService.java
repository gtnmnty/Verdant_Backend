package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.verdant.salon_ecomm.dtos.user.*;
import com.verdant.salon_ecomm.dtos.user.events.UserEmailChangeRequestedEvent;
import com.verdant.salon_ecomm.dtos.user.events.UserDeletedEvent;
import com.verdant.salon_ecomm.dtos.user.events.UserEmailChangedEvent;
import com.verdant.salon_ecomm.dtos.user.events.UserPasswordChangedEvent;
import com.verdant.salon_ecomm.dtos.user.events.UserProfileUpdatedEvent;
import com.verdant.salon_ecomm.models.entities.Address;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.exceptions.DuplicateEmailException;
import com.verdant.salon_ecomm.exceptions.ForbiddenException;
import com.verdant.salon_ecomm.exceptions.InvalidCredentialsException;
import com.verdant.salon_ecomm.exceptions.InvalidVerificationCodeException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.UserMapper;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.repositories.*;
import jakarta.validation.Validator;
import com.verdant.salon_ecomm.validation.ImageUploadValidator;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PublicFormRateLimiter rateLimiter;
    private final Validator validator;

    private final CloudinaryService cloudinaryService;
    private final CloudinaryAsyncCleanup cloudinaryAsyncCleanup;
    private final GiftCardService giftCardService;
    private final TransactionTemplate transactionTemplate;
    private final UserDataCleanup userDataCleanup;

    public UserDto.Profile getUserById(UUID id) {
        return userRepository.findById(id)
            .map(userMapper::toProfile)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Transactional
    public UserDto.Profile updateUserProfile(UUID id, UpdateUserRequest request) {
        var user = findUserOrThrow(id);

        // Email is never written here (the mapper ignores it). A different address only
        // starts a pending change, and the swap happens in confirmEmailChange() once the
        // user proves they own the new mailbox. Same address in a different case is a no-op.
        String requestedEmail = request.getEmail() == null
            ? null
            : request.getEmail().trim().toLowerCase(Locale.ROOT);
        boolean emailChangeRequested = requestedEmail != null
            && !requestedEmail.equalsIgnoreCase(user.getEmail());

        if (emailChangeRequested) {
            // GraphQL inputs aren't bean-validated (enabling it would also enforce AddressInput),
            // so check just the email constraints from UpdateUserRequest by hand.
            if (requestedEmail.isEmpty() || !validator.validateValue(
                    UpdateUserRequest.class, "email", requestedEmail).isEmpty()) {
                throw BusinessRuleException.invalid("Invalid email address.");
            }

            // Caps password guesses through this endpoint for anyone holding a stolen access token.
            rateLimiter.check("email-change:user:" + id, 5, Duration.ofMinutes(15));

            if (request.getCurrentPassword() == null
                || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
                throw new InvalidCredentialsException("Enter your current password to change your email.");
            }
            if (userRepository.existsByEmail(requestedEmail)) {
                throw new DuplicateEmailException("Email already in use");
            }
        }

        String previousFullName = user.getFullName();
        String previousPhone = user.getPhone();
        // Snapshot the address as a string before updateEntity() replaces it,
        // so the change check compares values rather than object references.
        String previousAddressSignature = addressSignature(user.getAddress());

        userMapper.updateEntity(request, user);

        // Plain code goes out by email (via the event); only its BCrypt hash is stored.
        // A newer request simply overwrites an older pending one, so only the latest code works.
        String plainCode = null;
        if (emailChangeRequested) {
            plainCode = String.valueOf(SECURE_RANDOM.nextInt(900000) + 100000);
            user.setPendingEmail(requestedEmail);
            user.setPendingEmailCode(passwordEncoder.encode(plainCode));
            user.setPendingEmailCodeExpiration(
                OffsetDateTime.now().plusMinutes(AuthenticationService.EMAIL_CHANGE_CODE_TTL_MINUTES));
        }

        User saved = userRepository.save(user);

        if (emailChangeRequested) {
            eventPublisher.publishEvent(new UserEmailChangeRequestedEvent(saved.getId(), requestedEmail, plainCode));
        }

        // Email isn't in this list: it hasn't changed yet. confirmEmailChange() records it.
        // M-22: phone and address are personal data (sensitive = true), so the audit entry says only that
        // they changed, not the old and new values. The name stays visible: staff need it to recognize the account.
        List<UserProfileUpdatedEvent.FieldChange> changes = new ArrayList<>();
        if (!Objects.equals(previousFullName, saved.getFullName())) {
            changes.add(new UserProfileUpdatedEvent.FieldChange("fullName", previousFullName, saved.getFullName(), false));
        }
        if (!Objects.equals(previousPhone, saved.getPhone())) {
            changes.add(new UserProfileUpdatedEvent.FieldChange("phone", previousPhone, saved.getPhone(), true));
        }

        if (!Objects.equals(previousAddressSignature, addressSignature(saved.getAddress()))) {
            changes.add(new UserProfileUpdatedEvent.FieldChange("address", null, null, true));
        }

        if (!changes.isEmpty()) {
            eventPublisher.publishEvent(new UserProfileUpdatedEvent(saved, changes));
        }

        return userMapper.toProfile(saved);
    }

    // Step 2 of the self-service email change: the code from the new mailbox swaps the address in.
    @Transactional
    public UserDto.Profile confirmEmailChange(UUID id, String code) {
        // 5 tries per 15 minutes against a 6-digit code that lives 15 minutes.
        rateLimiter.check("email-confirm:user:" + id, 5, Duration.ofMinutes(15));

        // Row lock: two concurrent confirms can't both swap the email.
        User user = userRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        OffsetDateTime now = OffsetDateTime.now();
        boolean codeValid = code != null
            && user.getPendingEmail() != null
            && user.getPendingEmailCode() != null
            && user.getPendingEmailCodeExpiration() != null
            && user.getPendingEmailCodeExpiration().isAfter(now)
            && passwordEncoder.matches(code.trim(), user.getPendingEmailCode());
        if (!codeValid) {
            throw new InvalidVerificationCodeException("Invalid or expired code.");
        }

        // Re-check: someone may have registered this address since the request was made.
        String newEmail = user.getPendingEmail();
        if (userRepository.existsByEmail(newEmail)) {
            throw new DuplicateEmailException("Email already in use");
        }

        String oldEmail = user.getEmail();
        user.setEmail(newEmail);
        // The user just proved they control this mailbox.
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);
        user.setPendingEmail(null);
        user.setPendingEmailCode(null);
        user.setPendingEmailCodeExpiration(null);
        // A forgot-password code or reset link mailed to the old address must not outlive the change.
        user.setResetPasswordCode(null);
        user.setResetPasswordCodeExpiration(null);
        passwordResetTokenRepository.invalidateAllActiveTokensForUser(id, now);

        // M-36: flushed so a simultaneous registration of the same address trips the unique lower(email)
        // index here and becomes the normal "already in use" error, not a failure at commit.
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new DuplicateEmailException("Email already in use");
        }

        // Refresh tokens are kept on purpose: the change was password- and code-verified.
        // The current access JWT names the old email, so the next request gets 401 and the
        // frontend's refresh issues a token for the new one with no re-login.
        eventPublisher.publishEvent(new UserEmailChangedEvent(saved.getId(), oldEmail, newEmail));
        // M-22: sensitive, so the audit entry reads "email: updated" instead of storing both addresses.
        eventPublisher.publishEvent(new UserProfileUpdatedEvent(saved,
            List.of(new UserProfileUpdatedEvent.FieldChange("email", oldEmail, newEmail, true))));

        return userMapper.toProfile(saved);
    }

    @Transactional
    public void updateUserPassword(UUID id, ChangePasswordRequest request) {
        var user = findUserOrThrow(id);

        if (AccountStatus.SUSPENDED.equals(user.getStatus()) || AccountStatus.BANNED.equals(user.getStatus())
            || AccountStatus.DELETED.equals(user.getStatus())) {
            throw new ForbiddenException("Account is " + user.getStatus().toString().toLowerCase());
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new ForbiddenException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // No save() needed: user is managed in this transaction, so the change is flushed on commit.

        // M-2: revoke every refresh token, like the forgot-password reset already does. Someone changing
        // their password because the account was compromised must also sign the attacker out.
        // Same transaction, so a failed change keeps the sessions. UserRESTController.changePassword then
        // issues a fresh token for the device that made the change, so only that one stays signed in.
        // Limit: an access JWT already handed out stays valid until it expires (JWT_EXPIRATION).
        refreshTokenRepository.deleteByUserId(id);

        eventPublisher.publishEvent(new UserPasswordChangedEvent(user));
    }

    @Transactional
    public void deleteUserById(UUID id) {
        var user = userRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        // Must run BEFORE the email is overwritten below: the refund gift card is delivered to it.
        // Same transaction as the deletion, so the forfeit and the anonymization commit (or fail) together.
        giftCardService.forfeitAndRefundOnAccountDeletion(user, user.getEmail());

        String originalEmail = user.getEmail();
        String anonymizedEmail = "deleted-" + user.getId() + "@deleted.verdant.local";

        // M-35: published here, inside the deletion's transaction and before anything is anonymized, so the
        // AFTER_COMMIT audit listener runs exactly once, when (and only if) the deletion commits. It used to
        // be published by DeletedUserCleanupJob from a non-transactional @Scheduled method, where a
        // @TransactionalEventListener never fires, so account deletions were never audited.
        eventPublisher.publishEvent(new UserDeletedEvent(id));

        // M-28: the same routine the admin hard-delete uses (cart, favorites, notifications, tokens).
        userDataCleanup.deletePersonalRows(id);
        // M-39: personal data in the records that are kept (ticket contact details, newsletter
        // subscription, email in old audit text). Needs the original email, so it runs before the swap below.
        userDataCleanup.eraseFromRetainedRecords(id, originalEmail, anonymizedEmail);
        // Avatar and Stripe customer are removed by DeletedUserCleanupJob after commit (read before nulling).
        userDataCleanup.scheduleExternalCleanup(user);

        user.setFullName(UserDataCleanup.DELETED_USER_NAME);
        user.setEmail(anonymizedEmail);
        user.setPhone(null);
        user.setAddress(null);
        user.setAvatarUrl(null);
        user.setAvatarPublicId(null);
        user.setStripeCustomerId(null);
        user.setStripePmId(null);
        user.setDisplayBrand(null);
        user.setDisplayLast4(null);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setStatus(AccountStatus.DELETED);
        user.setEnabled(false);
        user.setEmailVerified(false);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiration(null);
        // A pending address is personal data too, and must not be confirmable after deletion.
        user.setPendingEmail(null);
        user.setPendingEmailCode(null);
        user.setPendingEmailCodeExpiration(null);

        userRepository.save(user);
    }

    // Uploads the new avatar first, then swaps it in and deletes the old one only after the swap
    // committed: the user is never left without an avatar, and the old image is never deleted before
    // the new one is safely stored.
    // M-25: the file is checked first (real image type from its bytes, allow-list, 5 MB). Before, any
    // logged-in user could upload any file as an avatar (SVG, HTML, PDF) and get a public URL for it.
    // M-26: deliberately NOT @Transactional, so no DB connection is held during the Cloudinary calls.
    // Flow: validate -> user exists? -> upload (no DB connection) -> short TransactionTemplate swaps the
    // avatar -> on failure the new upload is deleted -> on success the old avatar is deleted in the
    // background (CloudinaryAsyncCleanup, with retries), not on the request thread.
    public UserDto.Profile updateAvatar(UUID id, MultipartFile file) {
        ImageUploadValidator.validate(file, "Avatar", ImageUploadValidator.DEFAULT_MAX_BYTES);
        findUserOrThrow(id); // 404 before uploading anything

        CloudinaryService.CloudinaryUploadResult uploaded = cloudinaryService.upload(file);

        AvatarSwap swap;
        try {
            swap = transactionTemplate.execute(tx -> {
                User user = findUserOrThrow(id);
                String previousPublicId = user.getAvatarPublicId();
                user.setAvatarUrl(uploaded.url());
                user.setAvatarPublicId(uploaded.publicId());
                return new AvatarSwap(userMapper.toProfile(userRepository.save(user)), previousPublicId);
            });
        } catch (RuntimeException e) {
            // Nothing references the new file: the swap never committed.
            try {
                cloudinaryService.delete(uploaded.publicId());
            } catch (RuntimeException cleanupEx) {
                e.addSuppressed(cleanupEx);
            }
            throw e;
        }

        if (swap.previousPublicId() != null && !swap.previousPublicId().equals(uploaded.publicId())) {
            cloudinaryAsyncCleanup.deleteWithRetry(swap.previousPublicId());
        }
        return swap.profile();
    }

    private record AvatarSwap(UserDto.Profile profile, String previousPublicId) {}

    // ----- Helpers -----------------------
    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("User not found with id: " + id)
            );
    }

    private String addressSignature(Address address) {
        if (address == null) return null;
        return String.join("|",
            Objects.toString(address.getLine1(), ""),
            Objects.toString(address.getLine2(), ""),
            Objects.toString(address.getCity(), ""),
            Objects.toString(address.getState(), ""),
            Objects.toString(address.getPostal(), ""),
            Objects.toString(address.getCountry(), "")
        );
    }
}