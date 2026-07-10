package com.verdant.salon_ecomm.dtos.appointment;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record UpdateAppointmentInput(
    UUID userId,
    UUID serviceId,
    UUID stylistId,
    OffsetDateTime scheduledAt,
    Integer guests,
    Map<String, Object> homeAddress,
    String notes
) {}
