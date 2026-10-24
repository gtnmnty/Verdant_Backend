package com.verdant.salon_ecomm.services.events.listeners;

import com.verdant.salon_ecomm.dtos.user.events.UserDeletedEvent;
import com.verdant.salon_ecomm.dtos.user.events.UserPasswordChangedEvent;
import com.verdant.salon_ecomm.dtos.user.events.UserProfileUpdatedEvent;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserAuditListener {

    private final AuditLogService auditLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserProfileUpdated(UserProfileUpdatedEvent event) {
        if (!event.hasChanges()) return;

        auditLogService.recordSelfService(
            AuditEntityType.ACCOUNT,
            event.user().getId(),
            AuditActionType.UPDATED,
            "Account details updated",
            event.changeSummary(),
            event.user()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserPasswordChanged(UserPasswordChangedEvent event) {
        auditLogService.recordSelfService(
            AuditEntityType.ACCOUNT,
            event.user().getId(),
            AuditActionType.SECURITY,
            "Password changed",
            "Password changed by account holder",
            event.user()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDeleted(UserDeletedEvent event) {
        auditLogService.recordSelfService(
            AuditEntityType.ACCOUNT,
            event.user().getId(),
            AuditActionType.DELETED,
            "Account deleted",
            event.user().getFullName() + " deleted their own account",
            event.user()
        );
    }
}
