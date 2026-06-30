package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.user.*;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.exceptions.DuplicateEmailException;
import com.verdant.salon_ecomm.exceptions.ForbiddenException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.UserMapper;
import com.verdant.salon_ecomm.models.enums.AccountRole;
import com.verdant.salon_ecomm.models.enums.AccountStatus;
import com.verdant.salon_ecomm.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserDto.Profile getUserById(UUID id) {
        return userRepository.findById(id)
            .map(userMapper::toProfile)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Transactional
    public UserDto.Summary registerUser(RegisterUserDto request){
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("Email already in use");
        }

        var newUser = userMapper.toEntity(request);
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newUser.setRole(AccountRole.CUSTOMER);
        newUser.setStatus(AccountStatus.ACTIVE);

        var savedUser = userRepository.save(newUser);

        return userMapper.toSummary(savedUser);
    }

    @Transactional
    public UserDto.Profile updateUserProfile(UUID id, UpdateUserRequest request){
        var user = findUserOrThrow(id);

        userMapper.updateEntity(request, user);
        userRepository.save(user);
        return userMapper.toProfile(user);
    }

    @Transactional
    public void updateUserPassword(UUID id, ChangePasswordRequest request) {
        var user = findUserOrThrow(id);

        if (AccountStatus.SUSPENDED.equals(user.getStatus())) {
            throw new ForbiddenException("Account is suspended");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new ForbiddenException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        // User is a managed JPA entity within @Transactional Annotation
        // Changes are flushed automatically.
        // Saving explicitly is fine and makes intent clearer.
    }

    @Transactional
    public void deleteUserById(UUID id) {
        var user = findUserOrThrow(id);
        userRepository.delete(user);
    }

    // Callback or global
    private User findUserOrThrow(UUID id){
        return userRepository.findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("User not found with id: " + id)
            );
    }
}