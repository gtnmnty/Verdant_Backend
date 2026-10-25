package com.verdant.salon_ecomm.services.events.listeners.branch;

import com.verdant.salon_ecomm.dtos.branch.events.BranchCreatedEvent;
import com.verdant.salon_ecomm.dtos.branch.events.BranchUpdatedEvent;
import com.verdant.salon_ecomm.dtos.branch.events.BranchesBulkDeletedEvent;
import com.verdant.salon_ecomm.models.entities.Branch;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.utils.StaffNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BranchNotificationListener {

    // M-38: staff fan-out goes through StaffNotifier (active staff only, one transaction, failures isolated).
    private final StaffNotifier staffNotifier;

    // M-29: single-branch create and update now notify staff, like the other catalog changes.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBranchCreated(BranchCreatedEvent event) {
        Branch branch = event.branch();
        staffNotifier.notify(ReferenceType.BRANCH, branch.getId(), NotificationType.BRANCH_ADDED,
            "Branch added", branch.getName() + " was added.", event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBranchUpdated(BranchUpdatedEvent event) {
        if (!event.hasChanges()) return;
        Branch branch = event.branch();
        staffNotifier.notify(ReferenceType.BRANCH, branch.getId(), NotificationType.BRANCH_UPDATED,
            "Branch updated", branch.getName() + " was updated: " + String.join(", ", event.changedFields()) + ".",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBranchesBulkDeleted(BranchesBulkDeletedEvent event) {
        if (event.branches().isEmpty()) return;

        String actorName = event.actor() != null ? event.actor().getFullName() : null;
        String by = actorName != null ? " by " + actorName : "";
        Branch first = event.branches().getFirst();

        // M-30: a single delete arrives here too (deleteBranch routes through deleteBranches).
        if (event.branches().size() == 1) {
            staffNotifier.notify(ReferenceType.BRANCH, first.getId(), NotificationType.BRANCH_DELETED,
                "Branch deleted", first.getName() + " was deleted" + by + ".", event.actor());
            return;
        }
        staffNotifier.notify(ReferenceType.BRANCH, first.getId(), NotificationType.BULK_ACTION_PERFORMED,
            "Bulk branch deletion", event.branches().size() + " branches were deleted" + by + ".", event.actor());
    }
}
