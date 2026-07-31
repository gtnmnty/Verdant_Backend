package com.verdant.salon_ecomm.dtos.appointment.events;

import com.verdant.salon_ecomm.entities.Appointment;
import com.verdant.salon_ecomm.entities.User;

import java.util.List;

public record AppointmentUpdatedEvent(Appointment appointment, User actor, List<FieldChange> changes) {

    public record FieldChange(String field, String oldValue, String newValue) {
        @Override
        public String toString() {
            return field + ": " + oldValue + " -> " + newValue;
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
