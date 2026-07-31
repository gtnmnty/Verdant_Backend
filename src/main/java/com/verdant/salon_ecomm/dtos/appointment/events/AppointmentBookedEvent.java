package com.verdant.salon_ecomm.dtos.appointment.events;

import com.verdant.salon_ecomm.entities.Appointment;

public record AppointmentBookedEvent(Appointment appointment) {
}
