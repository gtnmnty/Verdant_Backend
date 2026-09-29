package com.verdant.salon_ecomm.dtos.appointment.events;

import com.verdant.salon_ecomm.models.entities.Appointment;
import com.verdant.salon_ecomm.models.entities.User;

public record AppointmentCancelledEvent(Appointment appointment, User actor) {
    public boolean isSelfService() {
        return actor != null && appointment.getUser() != null
            && actor.getId().equals(appointment.getUser().getId());
    }
}
