package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.user.*;
import com.verdant.salon_ecomm.dtos.user.events.UserDeletedEvent;
import com.verdant.salon_ecomm.dtos.user.events.UserPasswordChangedEvent;
import com.verdant.salon_ecomm.dtos.user.events.UserProfileUpdatedEvent;
import com.verdant.salon_ecomm.dtos.user.events.UserRegisteredEvent;
import com.verdant.salon_ecomm.entities.PendingCleanUpJob;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.exceptions.DuplicateEmailException;
import com.verdant.salon_ecomm.exceptions.ForbiddenException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.UserMapper;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.repositories.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    // Stable per-JVM identity used to claim cleanup jobs, so only this
    // instance processes the batch it claimed. Doesn't need to survive
    // restarts — an unfinished claim just expires and gets picked up by
    // whichever instance runs the next scheduled pass.
    private final String instanceId = UUID.randomUUID().toString();
    private static final int CLEANUP_BATCH_SIZE = 50;
    private static final long CLEANUP_LEASE_MINUTES = 5;

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final CartItemRepository cartItemRepository;
    private final FavoriteRepository favoriteRepository;
    private final NotificationRepository notificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final CleanUpJobRepository cleanUpJobRepository;
    private final CleanUpJobTransactions cleanUpJobTransactions;

    private final CloudinaryService cloudinaryService;
    private final PaymentService paymentService;

    public UserDto.Profile getUserById(UUID id) {
        return userRepository.findById(id)
            .map(userMapper::toProfile)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Transactional
    public UserDto.Summary registerUser(RegisterUserDto request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException("Email already in use");
        }

        var newUser = userMapper.toEntity(request);
        newUser.setEmail(normalizedEmail);
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newUser.setRole(AccountRole.CUSTOMER);
        newUser.setStatus(AccountStatus.ACTIVE);

        var savedUser = userRepository.save(newUser);

        eventPublisher.publishEvent(new UserRegisteredEvent(savedUser));

        return userMapper.toSummary(savedUser);
    }

    @Transactional
    public UserDto.Profile updateUserProfile(UUID id, UpdateUserRequest request) {
        var user = findUserOrThrow(id);

        if (request.getEmail() != null) {
            String normalizedEmail = request.getEmail().trim().toLowerCase();
            if (!normalizedEmail.equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmail(normalizedEmail)) {
                throw new DuplicateEmailException("Email already in use");
            }
        }

        String previousFullName = user.getFullName();
        String previousEmail = user.getEmail();
        String previousPhone = user.getPhone();
        // ADDED: snapshot address fields before updateEntity() mutates the
        // embedded Address in place — capturing the reference alone wouldn't
        // work, since it's the same object before and after.
        String previousAddressSignature = addressSignature(user.getAddress());

        userMapper.updateEntity(request, user);
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail().trim().toLowerCase());
        }

        User saved = userRepository.save(user);

        List<UserProfileUpdatedEvent.FieldChange> changes = new ArrayList<>();
        if (!Objects.equals(previousFullName, saved.getFullName())) {
            changes.add(new UserProfileUpdatedEvent.FieldChange("fullName", previousFullName, saved.getFullName()));
        }
        if (!Objects.equals(previousEmail, saved.getEmail())) {
            changes.add(new UserProfileUpdatedEvent.FieldChange("email", previousEmail, saved.getEmail()));
        }
        if (!Objects.equals(previousPhone, saved.getPhone())) {
            changes.add(new UserProfileUpdatedEvent.FieldChange("phone", previousPhone, saved.getPhone()));
        }
        // ADDED: address-only edits were previously invisible to the audit log.
        // Values are intentionally omitted (null/null) — only the fact that the
        // address changed is recorded, not the address itself.
        if (!Objects.equals(previousAddressSignature, addressSignature(saved.getAddress()))) {
            changes.add(new UserProfileUpdatedEvent.FieldChange("address", null, null));
        }

        if (!changes.isEmpty()) {
            eventPublisher.publishEvent(new UserProfileUpdatedEvent(saved, changes));
        }

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
        // User is a managed JPA entity within @Transactional Annotation
        // Changes are flushed automatically.
        // Saving explicitly is fine and makes intent clearer.

        eventPublisher.publishEvent(new UserPasswordChangedEvent(user));
    }

    @Transactional
    public void deleteUserById(UUID id) {
        var user = userRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        String avatarPublicId = user.getAvatarPublicId();
        String stripeCustomerId = user.getStripeCustomerId();

        cartItemRepository.deleteByUserId(id);
        favoriteRepository.deleteByUserId(id);
        notificationRepository.deleteByUserId(id);
        refreshTokenRepository.deleteByUserId(id);
        passwordResetTokenRepository.deleteByUserId(id);

        user.setFullName("Deleted User");
        user.setEmail("deleted-" + user.getId() + "@deleted.verdant.local");
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

        userRepository.save(user);

        // Record what still needs cleaning up externally. This commits atomically
        // with the user deletion — if this transaction commits, we're guaranteed
        // to eventually process (and retry) the external cleanup, even if the
        // app crashes right after this method returns.
        cleanUpJobRepository.save(PendingCleanUpJob.builder()
            .userId(id)
            .avatarPublicId(avatarPublicId)
            .stripeCustomerId(stripeCustomerId)
            .build());

    }

    @Scheduled(fixedDelay = 60_000)
    public void processPendingCleanupJobs() {
        // Unique per invocation (not the fixed per-JVM instanceId) so two overlapping
        // runs of this method never share a claim token and can't collide on the same batch.
        String claimToken = instanceId + ":" + UUID.randomUUID();
        cleanUpJobTransactions.claimBatch(claimToken, CLEANUP_LEASE_MINUTES, CLEANUP_BATCH_SIZE);

        for (PendingCleanUpJob job : cleanUpJobRepository.findByClaimedByAndProcessedFalse(claimToken)) {
            try {
                // External calls run outside any DB transaction — no point holding
                // a DB connection/transaction open for the duration of an HTTP call.
                if (job.getAvatarPublicId() != null) {
                    cloudinaryService.delete(job.getAvatarPublicId());
                }
                if (job.getStripeCustomerId() != null) {
                    paymentService.deleteStripeCustomer(job.getStripeCustomerId());
                }
                cleanUpJobTransactions.markProcessed(job.getId(), claimToken);

                userRepository.findById(job.getUserId())
                    .ifPresent(user -> eventPublisher.publishEvent(new UserDeletedEvent(user)));
            } catch (Exception e) {
                cleanUpJobTransactions.incrementRetryCount(job.getId(), claimToken);
                log.error("Cleanup job {} failed for user {} (retry {})",
                    job.getId(), job.getUserId(), job.getRetryCount() + 1, e);
            }
        }
    }

    // Uploads the new avatar first, then swaps it in and deletes the old one
    // only after the swap succeeds — avoids leaving the user with no avatar
    // if the delete step were to fail, and avoids deleting the old image
    // before we're sure the new one actually made it to Cloudinary.
    // The old avatar is deleted only after the DB transaction actually commits —
    // if the transaction rolls back, the old image is left alone, and we clean
    // up the newly-uploaded (now-orphaned) image instead.
    @Transactional
    public UserDto.Profile updateAvatar(UUID id, MultipartFile file) {
        User user = findUserOrThrow(id);

        String previousPublicId = user.getAvatarPublicId();

        CloudinaryService.CloudinaryUploadResult uploaded = cloudinaryService.upload(file);

        user.setAvatarUrl(uploaded.url());
        user.setAvatarPublicId(uploaded.publicId());
        User saved = userRepository.save(user);

        boolean publicIdChanged = previousPublicId != null && !previousPublicId.equals(uploaded.publicId());

        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (publicIdChanged) {
                        cloudinaryService.delete(previousPublicId);
                    }
                }

                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        cloudinaryService.delete(uploaded.publicId());
                    }
                }
            }
        );

        return userMapper.toProfile(saved);
    }

    // ----- Helpers -----------------------
    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("User not found with id: " + id)
            );
    }

    private String addressSignature(com.verdant.salon_ecomm.entities.Address address) {
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