package com.verdant.salon_ecomm.dtos.appointment;

import com.verdant.salon_ecomm.entities.*;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentServiceType;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AdminAppointmentDto(
    UUID id,
    String appointmentCode,
    User user,
    SalonService service,
    String serviceName,
    AppointmentServiceType serviceType,
    BigDecimal priceSnapshot,
    Stylist stylist,
    Branch branch,
    OffsetDateTime scheduledAt,
    Integer durationMinutes,
    AppointmentStatus status,
    Short guests,
    Address homeAddress,
    String notes,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
