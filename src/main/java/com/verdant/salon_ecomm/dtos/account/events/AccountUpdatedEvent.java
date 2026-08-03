package com.verdant.salon_ecomm.dtos.account.events;

import com.verdant.salon_ecomm.entities.User;

import java.util.List;

public record AccountUpdatedEvent(User account, User actor, List<FieldChange> changes) {

    public record FieldChange(String field, String oldValue, String newValue, boolean sensitive) {
        @Override
        public String toString() {
            return field + ": " + oldValue + " -> " + newValue;
        }
    }

    public boolean hasChanges() {
        return changes != null && !changes.isEmpty();
    }

    public boolean hasSensitiveChanges() {
        return hasChanges() && changes.stream().anyMatch(FieldChange::sensitive);
    }

    public String changeSummary() {
        if (!hasChanges()) return "No field changes detected";
        return String.join("; ", changes.stream().map(FieldChange::toString).toList());
    }
}
