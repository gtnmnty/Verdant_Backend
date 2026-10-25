package com.verdant.salon_ecomm.services.events.listeners.account;

import com.verdant.salon_ecomm.dtos.account.events.AccountCreatedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountPasswordResetRequestedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountUpdatedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountsDeletedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountsSuspendedEvent;
import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.services.NotificationService;
import com.verdant.salon_ecomm.utils.StaffNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountNotificationListener {

    private final NotificationService notificationService;
    private final StaffNotifier staffNotifier;

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

        // M-22: field names only. changeSummary() carries old/new values, so a phone change used to be
        // broadcast to every staff member, receptionists included. The values stay in the audit entry.
        notifyStaff(NotificationType.ACCOUNT_UPDATED, "Account updated",
            "\"" + account.getFullName() + "\" was updated: " + event.changedFields(),
            account.getId(), event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountPasswordResetRequested(AccountPasswordResetRequestedEvent event) {
        // A just-created account gets its set-password email (AccountPasswordResetEmailListener);
        // a "reset requested, contact support" warning would only alarm a user who hasn't signed in yet.
        if (event.newAccount()) return;

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

    // M-38: both helpers delegate to StaffNotifier (active staff only, one transaction, per-recipient fallback).
    private void notifyStaff(NotificationType type, String title, String message, UUID referenceAccountId, User actor) {
        staffNotifier.notify(ReferenceType.USER, referenceAccountId, type, title, message, actor);
    }

    private void notifyStaffBulk(String title, String message, UUID referenceAccountId, UUID actorId, String actorName) {
        staffNotifier.notify(ReferenceType.USER, referenceAccountId, NotificationType.BULK_ACTION_PERFORMED,
            title, message, actorId, actorName);
    }
}