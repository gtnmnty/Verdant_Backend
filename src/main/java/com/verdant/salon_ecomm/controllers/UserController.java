package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.user.RegisterUserRequest;
import com.verdant.salon_ecomm.dtos.user.UpdateUserRequest;
import com.verdant.salon_ecomm.dtos.user.UserResponse;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.UserMapper;
import com.verdant.salon_ecomm.repositories.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@RestController
@RequestMapping("/users")
public class UserController {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @GetMapping
    public List<UserResponse.Admin> getAll() {

        return userRepository.findAll()
            .stream()
            .map(userMapper::toAdmin)
            .toList();
    }

    @GetMapping("/{id}")
    public UserResponse getOne(@PathVariable UUID id) {
        var user =  userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return userMapper.toDto(user);
    }

    @PostMapping
    public ResponseEntity<UserResponse> putUserDetails(@RequestBody RegisterUserRequest request, UriComponentsBuilder uriBuilder ) {
        var newUser = userMapper.toEntity(request);

        newUser.setRole("CUSTOMER");
        newUser.setStatus("ACTIVE");
        newUser.setAddress(newUser.getAddress());

        var savedUser = userRepository.save(newUser);
        var uri = uriBuilder.path("/users/{id}").buildAndExpand(savedUser.getId()).toUri();
        return ResponseEntity.created(uri).body(userMapper.toDto(savedUser));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<UserResponse> deleteUserDetails(@PathVariable UUID id) {
        var user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        userRepository.delete(user);

        return ResponseEntity.noContent().build();
    }

//    @PatchMapping("/{id}/change-password")
//    public ResponseEntity<UserResponse> changePassword(@RequestBody ChangePasswordRequest request, @PathVariable UUID id) {
//        var user = userRepository.findById(id)
//          .orElseThrow(() -> new ResourceNotFoundException("User not found"));

//        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
//
//        if ("SUSPENDED".equals(user.getStatus())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
//
//        if(!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())){
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
//        }
//
//        user.setPasswordHash(password.encoder(request.getNewPassword()));
//        userRepository.save(user);
//        return ResponseEntity.ok(userMapper.toDto(user));
//    }

    @PatchMapping("/{id}/profile")
    public ResponseEntity<UserResponse.Profile> updateUserProfile(@PathVariable UUID id, @RequestBody UpdateUserRequest request) {
        var user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        userMapper.updateEntity(request, user);
        var updatedUser = userRepository.save(user);
        return ResponseEntity.ok(userMapper.toProfile(updatedUser));
    }


}
