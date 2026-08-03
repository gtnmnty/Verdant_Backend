package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.account.events.AccountCreatedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountPasswordResetRequestedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountUpdatedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountsDeletedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountsSuspendedEvent;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AccountAuditListener {

    private final AuditLogService auditLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountCreated(AccountCreatedEvent event) {
        User account = event.account();
        auditLogService.record(
            AuditEntityType.ACCOUNT,
            account.getId(),
            AuditActionType.CREATED,
            "Account \"" + account.getFullName() + "\" created (" + account.getRole() + ")",
            null,
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountUpdated(AccountUpdatedEvent event) {
        if (!event.hasChanges()) return;

        User account = event.account();
        String title = (event.hasSensitiveChanges() ? "[SECURITY] " : "")
            + "Account \"" + account.getFullName() + "\" updated";

        auditLogService.record(
            AuditEntityType.ACCOUNT,
            account.getId(),
            AuditActionType.UPDATED,
            title,
            event.changeSummary(),
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountPasswordResetRequested(AccountPasswordResetRequestedEvent event) {
        auditLogService.record(
            AuditEntityType.ACCOUNT,
            event.accountId(),
            AuditActionType.SECURITY,
            "Password reset requested for " + event.email(),
            "Reset link issued by staff",
            event.actor()
        );
    }

    // One entry for the whole batch, not one per account.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountsSuspended(AccountsSuspendedEvent event) {
        if (event.accounts().isEmpty()) return;
        int count = event.accounts().size();

        String affectedAccounts = event.accounts().stream()
            .map(account -> account.getFullName() + " (" + account.getId() + ")")
            .collect(Collectors.joining(", "));

        auditLogService.record(
            AuditEntityType.ACCOUNT,
            event.accounts().getFirst().getId(),
            AuditActionType.STATUS_CHANGED,
            count + " accounts suspended",
            "Bulk suspended by staff. Affected accounts: " + affectedAccounts,
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountsDeleted(AccountsDeletedEvent event) {
        if (event.accounts().isEmpty()) return;
        int count = event.accounts().size();

        // Captured before the transaction commits (event.accounts() holds the
        // in-memory entities from before deleteAllInBatch), so full identifying
        // detail is preserved even though the rows themselves are now gone.
        String affectedAccounts = event.accounts().stream()
            .map(account -> account.getFullName() + " (" + account.getId() + ")")
            .collect(Collectors.joining(", "));

        auditLogService.record(
            AuditEntityType.ACCOUNT,
            event.accounts().getFirst().getId(),
            AuditActionType.BULK_DELETED,
            count + " accounts deleted",
            "Bulk deleted by staff. Deleted accounts: " + affectedAccounts,
            event.actor()
        );
    }
}