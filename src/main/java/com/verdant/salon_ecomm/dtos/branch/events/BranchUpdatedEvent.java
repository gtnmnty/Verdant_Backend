package com.verdant.salon_ecomm.dtos.branch.events;

import com.verdant.salon_ecomm.models.entities.Branch;
import com.verdant.salon_ecomm.models.entities.User;

import java.util.List;

// M-29: single-branch update used to publish nothing. changedFields holds only field names (e.g. "phone",
// "status"), so the audit entry says what changed without copying contact details into it.
public record BranchUpdatedEvent(Branch branch, User actor, List<String> changedFields) {

    public boolean hasChanges() {
        return changedFields != null && !changedFields.isEmpty();
    }
}
