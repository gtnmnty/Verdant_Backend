package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.models.entities.PendingCleanUpJob;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterStatus;
import com.verdant.salon_ecomm.repositories.*;
import com.verdant.salon_ecomm.repositories.newsletter.NewsletterSubscriberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

// M-28: the one routine both account-deletion paths use, so they can no longer drift apart.
//   - Self-service deletion (UserService.deleteUserById) anonymizes the user row and keeps it.
//   - Admin hard-delete (AccountService.deleteAccounts) removes the row.
// Before, self-deletion cleared cart items, favorites, notifications and tokens, while the admin path's
// deleteAllInBatch skipped all of that and relied entirely on the V2 ON DELETE CASCADE constraints. On a
// database where those constraints were missing, the delete failed with a foreign-key error (500), and
// the user's avatar and Stripe customer were never cleaned up at all.
// MANDATORY: runs inside the caller's transaction, so the rows go together with the account change, or
// not at all.
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class UserDataCleanup {

    // What erased personal data is replaced with in records that are kept (tickets, audit text).
    public static final String DELETED_USER_NAME = "Deleted User";

    private final CartItemRepository cartItemRepository;
    private final FavoriteRepository favoriteRepository;
    private final NotificationRepository notificationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final CleanUpJobRepository cleanUpJobRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final NewsletterSubscriberRepository newsletterSubscriberRepository;
    private final AuditLogRepository auditLogRepository;

    // Rows that only exist for this user and mean nothing without them. Deleting the refresh tokens also
    // signs the user out everywhere (their access JWT stops working at its next refresh).
    public void deletePersonalRows(UUID userId) {
        cartItemRepository.deleteByUserId(userId);
        favoriteRepository.deleteByUserId(userId);
        notificationRepository.deleteByUserId(userId);
        refreshTokenRepository.deleteByUserId(userId);
        passwordResetTokenRepository.deleteByUserId(userId);
    }

    // M-39: personal data in records that are KEPT after the account is gone. Anonymizing the user row
    // left these behind:
    //   - support tickets: contact name and email from the contact form -> replaced, the ticket thread stays
    //   - newsletter subscription for the same address -> unsubscribed and its address replaced. Anonymized
    //     rather than deleted: newsletter_deliveries rows point at it, and deleting would also change the
    //     sent/failed counts of past campaigns
    //   - audit titles and details that contain the address in plain text (entries written before M-22)
    // Must be called with the email the account had BEFORE anonymization.
    public void eraseFromRetainedRecords(UUID userId, String email, String anonymizedEmail) {
        supportTicketRepository.anonymizeContactForUser(userId, DELETED_USER_NAME, anonymizedEmail);

        if (email == null || email.isBlank()) return;
        newsletterSubscriberRepository.findByEmail(email).ifPresent(subscriber -> {
            subscriber.setEmail(anonymizedEmail);
            subscriber.setStatus(NewsletterStatus.UNSUBSCRIBED);
            if (subscriber.getUnsubscribedAt() == null) subscriber.setUnsubscribedAt(OffsetDateTime.now());
        });
        auditLogRepository.redactEmail(email, "[deleted user]");
    }

    // Queues the avatar and Stripe customer for DeletedUserCleanupJob, which deletes them outside any DB
    // transaction and retries on failure. The job row commits with the deletion, so the cleanup is
    // guaranteed to happen once the account is gone. Skipped when there is nothing external to remove.
    public void scheduleExternalCleanup(User user) {
        if (user.getAvatarPublicId() == null && user.getStripeCustomerId() == null) return;
        cleanUpJobRepository.save(PendingCleanUpJob.builder()
            .userId(user.getId())
            .avatarPublicId(user.getAvatarPublicId())
            .stripeCustomerId(user.getStripeCustomerId())
            .build());
    }
}
