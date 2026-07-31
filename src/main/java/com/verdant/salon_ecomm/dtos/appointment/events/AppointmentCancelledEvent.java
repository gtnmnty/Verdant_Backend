package com.verdant.salon_ecomm.dtos.appointment.events;

import com.verdant.salon_ecomm.entities.Appointment;
import com.verdant.salon_ecomm.entities.User;

public record AppointmentCancelledEvent(Appointment appointment, User actor) {
    public boolean isSelfService() {
        return actor != null && appointment.getUser() != null
            && actor.getId().equals(appointment.getUser().getId());
    }
}
