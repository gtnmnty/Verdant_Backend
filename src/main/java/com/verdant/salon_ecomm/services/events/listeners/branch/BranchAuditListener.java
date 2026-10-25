package com.verdant.salon_ecomm.services.events.listeners.branch;

import com.verdant.salon_ecomm.dtos.branch.events.BranchCreatedEvent;
import com.verdant.salon_ecomm.dtos.branch.events.BranchUpdatedEvent;
import com.verdant.salon_ecomm.dtos.branch.events.BranchesBulkDeletedEvent;
import com.verdant.salon_ecomm.models.entities.Branch;
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

    // M-29: single-branch create and update were not audited at all.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBranchCreated(BranchCreatedEvent event) {
        Branch branch = event.branch();
        auditLogService.record(
            AuditEntityType.BRANCH,
            branch.getId(),
            AuditActionType.CREATED,
            "Branch \"" + branch.getName() + "\" created",
            "Status " + branch.getStatus(),
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBranchUpdated(BranchUpdatedEvent event) {
        if (!event.hasChanges()) return;
        Branch branch = event.branch();
        auditLogService.record(
            AuditEntityType.BRANCH,
            branch.getId(),
            AuditActionType.UPDATED,
            "Branch \"" + branch.getName() + "\" updated",
            "Changed: " + String.join(", ", event.changedFields()),
            event.actor()
        );
    }

    // One entry for the whole batch, not one per branch — matches the
    // convention used for bulk cancel/delete across the other modules.
    // M-30: a single delete now goes through deleteBranches too, so it is recorded here (by name).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBranchesBulkDeleted(BranchesBulkDeletedEvent event) {
        if (event.branches().isEmpty()) return;
        int count = event.branches().size();
        Branch first = event.branches().getFirst();

        auditLogService.record(
            AuditEntityType.BRANCH,
            first.getId(),
            count == 1 ? AuditActionType.DELETED : AuditActionType.BULK_DELETED,
            count == 1 ? "Branch \"" + first.getName() + "\" deleted" : count + " branches deleted",
            count == 1 ? "Deleted by staff" : "Bulk deleted by staff",
            event.actor()
        );
    }
}
