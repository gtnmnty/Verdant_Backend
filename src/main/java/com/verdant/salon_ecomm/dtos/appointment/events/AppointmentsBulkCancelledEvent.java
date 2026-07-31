package com.verdant.salon_ecomm.dtos.appointment.events;

import com.verdant.salon_ecomm.entities.Appointment;
import com.verdant.salon_ecomm.entities.User;

import java.util.List;

// Covers a staff-initiated bulk cancel. Audit log and staff notification collapse
// into a single entry ("12 appointments cancelled by X") rather than one per item;
// affected customers still get their own individual notification.
public record AppointmentsBulkCancelledEvent(List<Appointment> appointments, User actor) {
}
