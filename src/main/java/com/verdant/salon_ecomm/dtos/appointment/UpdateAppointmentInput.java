package com.verdant.salon_ecomm.dtos.appointment;

import com.verdant.salon_ecomm.dtos.AddressInput;
import jakarta.validation.Valid;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateAppointmentInput(
    UUID userId,
    UUID serviceId,
    UUID stylistId,
    OffsetDateTime scheduledAt,
    Integer guests,
    @Valid AddressInput homeAddress,
    String notes
) {}
