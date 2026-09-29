package com.verdant.salon_ecomm.dtos.appointment.events;

import com.verdant.salon_ecomm.models.entities.Appointment;

public record AppointmentBookedEvent(Appointment appointment) {
}
