package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.branch.events.BranchesBulkDeletedEvent;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BranchAuditListener {

    private final AuditLogService auditLogService;

    // One entry for the whole batch, not one per branch — matches the
    // convention used for bulk cancel/delete across the other modules.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBranchesBulkDeleted(BranchesBulkDeletedEvent event) {
        if (event.branches().isEmpty()) return;
        int count = event.branches().size();

        auditLogService.record(
            AuditEntityType.BRANCH,
            event.branches().getFirst().getId(),
            AuditActionType.BULK_DELETED,
            count + " branches deleted",
            "Bulk deleted by staff",
            event.actor()
        );
    }
}
