package com.verdant.salon_ecomm.config;

import com.verdant.salon_ecomm.entities.User; // adjust import to your actual entity
import com.verdant.salon_ecomm.models.enums.AccountRole;
import com.verdant.salon_ecomm.repositories.UserRepository; // adjust to your actual repo
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Seeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        userRepository.findByEmail("bianca@verdant.com").ifPresentOrElse(
            user -> {
                // Fix existing plaintext password
                user.setPasswordHash(passwordEncoder.encode("123456789"));
                userRepository.save(user);
                System.out.println("Admin password re-hashed for bianca@verdant.com");
            },
            () -> {
                // Create if not exists
                User admin = User.builder()
                    .fullName("Bianca Reyes")
                    .email("bianca@verdant.com")
                    .passwordHash(passwordEncoder.encode("123456789"))
                    .role(AccountRole.CUSTOMER)
                    .enabled(true)
                    .build();
                userRepository.save(admin);
                System.out.println("Admin user created: bianca@verdant.com");
            }
        );
    }
}