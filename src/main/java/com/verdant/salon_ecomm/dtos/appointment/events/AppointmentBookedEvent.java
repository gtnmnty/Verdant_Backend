package com.verdant.salon_ecomm.dtos.appointment.events;

import com.verdant.salon_ecomm.models.entities.Appointment;
import com.verdant.salon_ecomm.models.entities.User;
import org.hibernate.Hibernate;

// actor is whoever made the booking: the customer themselves, or staff booking on their behalf.
public record AppointmentBookedEvent(Appointment appointment, User actor) {
    // Listeners run @Async after the response, outside the request's database session, so a lazy customer can't
    // be loaded then. Load it here, while the service's transaction is still open.
    public AppointmentBookedEvent {
        if (appointment != null) Hibernate.initialize(appointment.getUser());
    }

    public boolean isSelfService() {
        return actor != null && appointment.getUser() != null
            && actor.getId().equals(appointment.getUser().getId());
    }
}
