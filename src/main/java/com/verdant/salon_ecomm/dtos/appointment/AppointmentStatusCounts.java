package com.verdant.salon_ecomm.dtos.appointment;

public record AppointmentStatusCounts(
    long all,
    long pending,
    long upcoming,
    long completed,
    long cancelled
) {}
