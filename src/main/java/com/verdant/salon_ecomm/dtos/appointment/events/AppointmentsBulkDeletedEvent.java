package com.verdant.salon_ecomm.dtos.appointment.events;

import com.verdant.salon_ecomm.entities.Appointment;
import com.verdant.salon_ecomm.entities.User;

import java.util.List;

public record AppointmentsBulkDeletedEvent(List<Appointment> appointments, User actor) {
}
