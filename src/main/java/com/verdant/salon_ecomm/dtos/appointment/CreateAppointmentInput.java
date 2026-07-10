package com.verdant.salon_ecomm.dtos.appointment;

import com.verdant.salon_ecomm.models.enums.appointments.AppointmentServiceType;

import java.time.OffsetDateTime;
import java.util.UUID;

// homeAddress: replace `Object` with your project's actual AddressInput DTO class
// (the same one already backing `AddressInput` elsewhere in the schema).
public record CreateAppointmentInput(
    UUID userId,
    UUID serviceId,
    UUID stylistId,
    AppointmentServiceType serviceType,
    UUID branchId,
    OffsetDateTime scheduledAt,
    Integer guests,
    Object homeAddress,
    String notes
) {}
