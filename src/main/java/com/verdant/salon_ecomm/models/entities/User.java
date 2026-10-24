package com.verdant.salon_ecomm.models.entities;

import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AccountRole role = AccountRole.CUSTOMER;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AccountStatus status = AccountStatus.UNVERIFIED;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Embedded
    private Address address;

    @Column(name = "stripe_pm_id")
    private String stripePmId;

    @Column(name = "display_brand", length = 30)
    private String displayBrand;

    @Column(name = "display_last4", length = 4)
    private String displayLast4;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "avatar_public_id", length = 255)
    private String avatarPublicId;

    @Column(name = "verification_code")
    private String verificationCode;

    @Column(name = "verificationCode_expiration")
    private OffsetDateTime verificationCodeExpiration;

    @Column(name = "reset_password_code")
    private String resetPasswordCode;

    @Column(name = "reset_password_code_expiration")
    private OffsetDateTime resetPasswordCodeExpiration;

    // Self-service email change: the new address sits here until its code is confirmed,
    // so a stolen session can't silently move the account to another mailbox.
    @Column(name = "pending_email", length = 255)
    private String pendingEmail;

    // BCrypt hash of the code mailed to pendingEmail.
    @Column(name = "pending_email_code")
    private String pendingEmailCode;

    @Column(name = "pending_email_code_expiration")
    private OffsetDateTime pendingEmailCodeExpiration;

    @Column
    private boolean enabled;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired(){
        return true;
    }

    // Allow-list, so it fails closed: a null status (e.g. a builder call that sets status(null))
    // or a status added to the enum later counts as locked until it is listed here.
    // UNVERIFIED stays unlocked because isEnabled() already blocks it until the email is confirmed.
    @Override
    public boolean isAccountNonLocked() {
        return status == AccountStatus.ACTIVE
            || status == AccountStatus.UNVERIFIED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return emailVerified;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

}
