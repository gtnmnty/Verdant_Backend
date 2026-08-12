package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.account.events.AccountCreatedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountPasswordResetRequestedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountUpdatedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountsDeletedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountsSuspendedEvent;
import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountNotificationListener {

    private static final List<AccountRole> STAFF_ROLES = List.of(
        AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.OWNER
    );

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountCreated(AccountCreatedEvent event) {
        User account = event.account();
        notifyStaff(NotificationType.ACCOUNT_CREATED, "New account created",
            "\"" + account.getFullName() + "\" (" + account.getRole() + ") was added.",
            account.getId(), event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountUpdated(AccountUpdatedEvent event) {
        if (!event.hasChanges()) return;

        User account = event.account();
        NotificationPriority priority = event.hasSensitiveChanges()
            ? NotificationPriority.WARNING
            : NotificationPriority.INFO;

        UUID actorId = event.actor() != null ? event.actor().getId() : null;
        String actorName = event.actor() != null ? event.actor().getFullName() : null;

        // Let the account holder know their own account was changed by staff.
        notificationService.create(new NotificationCreateDto(
            account.getId(),
            NotificationType.ACCOUNT_UPDATED,
            "Your account was updated",
            event.changeSummary(),
            ReferenceType.USER,
            account.getId(),
            priority,
            actorId,
            actorName
        ));

        notifyStaff(NotificationType.ACCOUNT_UPDATED, "Account updated",
            "\"" + account.getFullName() + "\" was updated: " + event.changeSummary(),
            account.getId(), event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountPasswordResetRequested(AccountPasswordResetRequestedEvent event) {
        notificationService.create(new NotificationCreateDto(
            event.accountId(),
            NotificationType.PASSWORD_RESET_REQUESTED,
            "Password reset requested",
            "A password reset was requested for your account by staff. "
                + "If this wasn't expected, contact support immediately.",
            ReferenceType.USER,
            event.accountId(),
            NotificationPriority.WARNING,
            null,
            null
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountsSuspended(AccountsSuspendedEvent event) {
        if (event.accounts().isEmpty()) return;

        UUID actorId = event.actor() != null ? event.actor().getId() : null;
        String actorName = event.actor() != null ? event.actor().getFullName() : null;

        for (User account : event.accounts()) {
            notificationService.create(new NotificationCreateDto(
                account.getId(),
                NotificationType.ACCOUNT_STATUS_CHANGED,
                "Your account was suspended",
                "Your account was suspended by staff.",
                ReferenceType.USER,
                account.getId(),
                NotificationPriority.WARNING,
                actorId,
                actorName
            ));
        }

        notifyStaffBulk("Bulk account suspension",
            event.accounts().size() + " accounts were suspended" + (actorName != null ? " by " + actorName : "") + ".",
            event.accounts().getFirst().getId(), actorId, actorName);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountsDeleted(AccountsDeletedEvent event) {
        if (event.accounts().isEmpty()) return;

        UUID actorId = event.actor() != null ? event.actor().getId() : null;
        String actorName = event.actor() != null ? event.actor().getFullName() : null;

        notifyStaffBulk("Bulk account deletion",
            event.accounts().size() + " accounts were deleted" + (actorName != null ? " by " + actorName : "") + ".",
            null, actorId, actorName);
    }

    // ── Helpers ──────────────────────────────────────────

    private void notifyStaff(NotificationType type, String title, String message, UUID referenceAccountId, User actor) {
        UUID actorId = actor != null ? actor.getId() : null;
        String actorName = actor != null ? actor.getFullName() : null;

        List<User> staff = userRepository.findByRoleIn(STAFF_ROLES);
        for (User staffMember : staff) {
            // ADDED: isolate failures per recipient, same as notifyStaffBulk —
            // one bad notification shouldn't stop the rest of the staff from
            // being notified.
            try {
                notificationService.create(new NotificationCreateDto(
                    staffMember.getId(),
                    type,
                    title,
                    message,
                    ReferenceType.USER,
                    referenceAccountId,
                    NotificationPriority.INFO,
                    actorId,
                    actorName
                ));
            } catch (Exception e) {
                log.error("Failed to notify staff member {}", staffMember.getId(), e);
            }
        }
    }

    private void notifyStaffBulk(String title, String message, UUID referenceAccountId, UUID actorId, String actorName) {
        List<User> staff = userRepository.findByRoleIn(STAFF_ROLES);
        for (User staffMember : staff) {
            try {
                notificationService.create(new NotificationCreateDto(
                    staffMember.getId(),
                    NotificationType.BULK_ACTION_PERFORMED,
                    title,
                    message,
                    ReferenceType.USER,
                    referenceAccountId,
                    NotificationPriority.INFO,
                    actorId,
                    actorName
                ));
            } catch (Exception e) {
                // CHANGED: System.err.println → logger, and pass the
                // throwable so the stack trace is actually captured.
                log.error("Failed to notify staff member {}", staffMember.getId(), e);
            }
        }
    }
}