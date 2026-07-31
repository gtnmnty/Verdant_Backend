package com.verdant.salon_ecomm.dtos.appointment.events;

import com.verdant.salon_ecomm.entities.Appointment;
import com.verdant.salon_ecomm.entities.User;

import java.time.OffsetDateTime;

public record AppointmentRescheduledEvent(
    Appointment appointment,
    User actor,
    OffsetDateTime previousScheduledAt
) {
    public boolean isSelfService() {
        return actor != null && appointment.getUser() != null
            && actor.getId().equals(appointment.getUser().getId());
    }
}
