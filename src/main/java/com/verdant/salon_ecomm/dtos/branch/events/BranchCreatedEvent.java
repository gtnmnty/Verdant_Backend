package com.verdant.salon_ecomm.dtos.branch.events;

import com.verdant.salon_ecomm.models.entities.Branch;
import com.verdant.salon_ecomm.models.entities.User;

// M-29: single-branch create used to publish nothing, so it left no audit entry and staff weren't told.
public record BranchCreatedEvent(Branch branch, User actor) {
}
