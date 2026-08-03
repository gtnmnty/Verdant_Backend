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
import com.verdant.salon_ecomm.repositories.AppointmentRepository;
import com.verdant.salon_ecomm.repositories.OrderRepository;
import com.verdant.salon_ecomm.repositories.ReviewRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
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
    private final OrderRepository orderRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReviewRepository reviewRepository;

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

        if (AccountStatus.SUSPENDED.equals(user.getStatus()) || AccountStatus.BANNED.equals(user.getStatus())) {
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
        var user = findUserOrThrow(id);

        // Same integrity issue as the admin bulk-delete path: User rows are
        // referenced by Order/Appointment/Review with no cascade. A hard delete
        // here would either crash with a raw FK violation or (if the constraint
        // somehow allowed it) orphan that history. Block with a clear message
        // instead — a real "delete my account" flow should anonymize the user
        // and keep the row for order/audit integrity rather than removing it,
        // but that's a larger feature than a bug fix; flagging it rather than
        // silently building it here.
        Set<UUID> ids = Set.of(id);
        boolean hasOrders = !orderRepository.findDistinctUserIdsWithOrders(ids).isEmpty();
        boolean hasAppointments = !appointmentRepository.findDistinctUserIdsWithAppointments(ids).isEmpty();
        boolean hasReviews = !reviewRepository.findDistinctUserIdsWithReviews(ids).isEmpty();

        if (hasOrders || hasAppointments || hasReviews) {
            throw new ForbiddenException(
                "Your account has order, appointment, or review history and can't be deleted. "
                    + "Contact support to close your account."
            );
        }

        userRepository.delete(user);

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