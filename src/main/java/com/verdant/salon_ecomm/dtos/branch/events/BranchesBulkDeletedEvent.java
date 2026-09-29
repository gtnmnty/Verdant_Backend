package com.verdant.salon_ecomm.dtos.branch.events;

import com.verdant.salon_ecomm.models.entities.Branch;
import com.verdant.salon_ecomm.models.entities.User;

import java.util.List;

public record BranchesBulkDeletedEvent(List<Branch> branches, User actor) {
}
