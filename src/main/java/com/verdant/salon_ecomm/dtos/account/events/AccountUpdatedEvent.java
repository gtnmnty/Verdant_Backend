package com.verdant.salon_ecomm.dtos.account.events;

import com.verdant.salon_ecomm.models.entities.User;

import java.util.List;

public record AccountUpdatedEvent(User account, User actor, List<FieldChange> changes) {

    public record FieldChange(String field, String oldValue, String newValue, boolean sensitive) {
        @Override
        public String toString() {
            return sensitive
                ? field + ": updated"
                : field + ": " + oldValue + " -> " + newValue;
        }
    }

    public boolean hasChanges() {
        return changes != null && !changes.isEmpty();
    }

    public boolean hasSensitiveChanges() {
        return hasChanges() && changes.stream().anyMatch(FieldChange::sensitive);
    }

    // M-22: only the names of the changed fields ("fullName, phone"), for messages sent to all staff, which
    // must not repeat a customer's phone or email values.
    public String changedFields() {
        if (!hasChanges()) return "nothing";
        return String.join(", ", changes.stream().map(FieldChange::field).toList());
    }

    public String changeSummary() {
        if (!hasChanges()) return "No field changes detected";
        return String.join("; ", changes.stream().map(FieldChange::toString).toList());
    }
}
