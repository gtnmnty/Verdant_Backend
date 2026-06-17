package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.user.RegisterUserRequest;
import com.verdant.salon_ecomm.dtos.user.UpdateUserRequest;
import com.verdant.salon_ecomm.dtos.user.UserResponse;
import com.verdant.salon_ecomm.mappers.UserMapper;
import com.verdant.salon_ecomm.repositories.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Set;
import java.util.UUID;

@AllArgsConstructor
@RestController
@RequestMapping("/users")
public class UserController {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @GetMapping
    public Iterable<UserResponse> getAll(@RequestParam(required = false, defaultValue = "", name = "sort") String sort) {
        if (!Set.of("fullName", "email").contains(sort)) sort = "fullName";

        return userRepository.findAll(Sort.by(Sort.Direction.ASC, sort))
            .stream()
            .map(userMapper::toDto)
            .toList();
    }

    @PostMapping
    public ResponseEntity<UserResponse> putUserDetails(@RequestBody RegisterUserRequest request, UriComponentsBuilder uriBuilder ) {
        var newUser = userMapper.toEntity(request);

        newUser.setRole("CUSTOMER");
        newUser.setStatus("ACTIVE");
        newUser.setAddressCountry("PH");

        var savedUser = userRepository.save(newUser);
        var response = userMapper.toDto(savedUser);
        var uri = uriBuilder.path("/users/{id}").buildAndExpand(newUser.getId()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUserDetails(@PathVariable UUID id, @RequestBody UpdateUserRequest request) {
        var user = userRepository.findById(id).orElse(null);

        if (user == null) return ResponseEntity.notFound().build();

        userMapper.updateUser(request, user);
        userRepository.save(user);

        var updatedUser = userRepository.save(user);
        return ResponseEntity.ok(userMapper.toDto(updatedUser));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<UserResponse> deleteUserDetails(@PathVariable UUID id) {
        var user = userRepository.findById(id).orElse(null);

        if (user == null) return ResponseEntity.notFound().build();

        userRepository.delete(user);

        return ResponseEntity.noContent().build();
    }


}
