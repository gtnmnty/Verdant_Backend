package com.verdant.salon_ecomm.dtos.appointment;

import com.verdant.salon_ecomm.models.enums.appointments.AppointmentServiceType;

import java.util.UUID;

public record AppointmentRebookDto(
    UUID serviceId,
    String serviceName,
    AppointmentServiceType serviceType,
    UUID stylistId,
    String stylistName,
    UUID branchId,
    String branchName,
    Short guests
) {}