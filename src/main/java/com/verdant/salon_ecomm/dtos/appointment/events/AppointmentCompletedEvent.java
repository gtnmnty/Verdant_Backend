package com.verdant.salon_ecomm.dtos.appointment.events;

import com.verdant.salon_ecomm.models.entities.Appointment;
import com.verdant.salon_ecomm.models.entities.User;
import org.hibernate.Hibernate;

public record AppointmentCompletedEvent(Appointment appointment, User actor) {
    // Listeners run @Async after the response, outside the request's database session, so a lazy customer can't
    // be loaded then. Load it here, while the service's transaction is still open.
    public AppointmentCompletedEvent {
        if (appointment != null) Hibernate.initialize(appointment.getUser());
    }
}
