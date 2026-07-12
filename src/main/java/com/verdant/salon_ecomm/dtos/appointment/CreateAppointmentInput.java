package com.verdant.salon_ecomm.dtos.appointment;

import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentServiceType;
import jakarta.validation.Valid;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateAppointmentInput(
    UUID userId,
    UUID serviceId,
    UUID stylistId,
    AppointmentServiceType serviceType,
    UUID branchId,
    OffsetDateTime scheduledAt,
    Integer guests,
    @Valid AddressInput homeAddress,
    String notes
) {}
