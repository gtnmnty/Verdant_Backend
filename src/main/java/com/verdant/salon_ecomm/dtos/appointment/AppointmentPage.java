package com.verdant.salon_ecomm.dtos.appointment;

import com.verdant.salon_ecomm.entities.Appointment;

import java.util.List;

public record AppointmentPage(
    List<Appointment> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
