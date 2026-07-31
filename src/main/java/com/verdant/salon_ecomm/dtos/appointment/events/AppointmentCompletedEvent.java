package com.verdant.salon_ecomm.dtos.appointment.events;

import com.verdant.salon_ecomm.entities.Appointment;
import com.verdant.salon_ecomm.entities.User;

public record AppointmentCompletedEvent(Appointment appointment, User actor) {
}
