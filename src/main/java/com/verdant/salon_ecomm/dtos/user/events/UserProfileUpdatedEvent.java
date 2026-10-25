package com.verdant.salon_ecomm.dtos.user.events;

import com.verdant.salon_ecomm.models.entities.User;

import java.util.List;

public record UserProfileUpdatedEvent(User user, List<FieldChange> changes) {

    // M-22: sensitive = personal data (email, phone, address). Its old and new values are left out of
    // toString(), and so out of the audit entry built from changeSummary(), the same way the staff path
    // (AccountUpdatedEvent) already did for email. The values stay on the record for code that needs them.
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

    public String changeSummary() {
        if (!hasChanges()) return "No field changes detected";
        return String.join("; ", changes.stream().map(FieldChange::toString).toList());
    }
}
