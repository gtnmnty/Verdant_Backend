package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.user.*;
import com.verdant.salon_ecomm.dtos.user.events.UserDeletedEvent;
import com.verdant.salon_ecomm.dtos.user.events.UserPasswordChangedEvent;
import com.verdant.salon_ecomm.dtos.user.events.UserProfileUpdatedEvent;
import com.verdant.salon_ecomm.dtos.user.events.UserRegisteredEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final CartItemRepository cartItemRepository;
    private final FavoriteRepository favoriteRepository;
    private final NotificationRepository notificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final CloudinaryService cloudinaryService;
    private final PaymentService paymentService;

    public UserDto.Profile getUserById(UUID id) {
        return userRepository.findById(id)
            .map(userMapper::toProfile)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Transactional
    public UserDto.Summary registerUser(RegisterUserDto request){
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
    public UserDto.Profile updateUserProfile(UUID id, UpdateUserRequest request){
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

        // Capture external-resource identifiers before they're scrubbed below.
        String avatarPublicId = user.getAvatarPublicId();
        String stripeCustomerId = user.getStripeCustomerId();

        // Personal data revoked/cleaned up immediately regardless of history.
        cartItemRepository.deleteByUserId(id);
        favoriteRepository.deleteByUserId(id);
        notificationRepository.deleteByUserId(id);
        refreshTokenRepository.deleteByUserId(id);
        passwordResetTokenRepository.deleteByUserId(id);

        // Anonymize rather than delete the row itself
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

        // External cleanup must complete before the deletion event fires, so
        // downstream listeners never observe "deleted" while the avatar/customer
        // still exist upstream.
        if (avatarPublicId != null) {
            cloudinaryService.delete(avatarPublicId);
        }
        if (stripeCustomerId != null) {
            paymentService.deleteStripeCustomer(stripeCustomerId);
        }

        eventPublisher.publishEvent(new UserDeletedEvent(user));
    }

    // Callback or global
    private User findUserOrThrow(UUID id){
        return userRepository.findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("User not found with id: " + id)
            );
    }
}