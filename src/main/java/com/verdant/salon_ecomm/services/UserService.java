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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
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

        userMapper.updateEntity(request, user);
        // updateEntity() doesn't normalize case (it just copies request.getEmail()
        // verbatim via the "email" -> "email" match) — normalize here so a
        // mixed-case edit doesn't create a second-class duplicate of an existing email.
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
    @Transactional
    public void processPendingCleanupJobs() {
        OffsetDateTime leaseExpiry = OffsetDateTime.now().plusMinutes(CLEANUP_LEASE_MINUTES);
        cleanUpJobRepository.claimBatch(instanceId, leaseExpiry, CLEANUP_BATCH_SIZE);

        for (PendingCleanUpJob job : cleanUpJobRepository.findByClaimedByAndProcessedFalse(instanceId)) {
            try {
                if (job.getAvatarPublicId() != null) {
                    cloudinaryService.delete(job.getAvatarPublicId());
                }
                if (job.getStripeCustomerId() != null) {
                    paymentService.deleteStripeCustomer(job.getStripeCustomerId());
                }
                cleanUpJobRepository.markProcessed(job.getId());

                userRepository.findById(job.getUserId())
                    .ifPresent(user -> eventPublisher.publishEvent(new UserDeletedEvent(user)));
            } catch (Exception e) {
                cleanUpJobRepository.incrementRetryCount(job.getId());
                log.error("Cleanup job {} failed for user {} (retry {})",
                    job.getId(), job.getUserId(), job.getRetryCount() + 1, e);
            }
        }
    }


    // Callback or global
    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("User not found with id: " + id)
            );
    }
}