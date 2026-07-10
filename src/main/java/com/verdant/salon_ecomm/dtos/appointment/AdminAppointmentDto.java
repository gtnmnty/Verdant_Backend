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
    Branch branchId,
    OffsetDateTime scheduledAt,
    Integer durationMinutes,
    AppointmentStatus status,
    Short guests,
    Map<String, Object> homeAddress,
    String notes,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static AdminAppointmentDto from(Appointment appointment) {
        return new AdminAppointmentDto(
            appointment.getId(),
            appointment.getAppointmentCode(),
            appointment.getUser(),
            appointment.getService(),
            appointment.getServiceName(),
            appointment.getServiceType(),
            appointment.getPriceSnapshot(),
            appointment.getStylist(),
            appointment.getBranch(),
            appointment.getScheduledAt(),
            appointment.getDurationMinutes(),
            appointment.getStatus(),
            appointment.getGuests(),
            appointment.getHomeAddress(),
            appointment.getNotes(),
            appointment.getCreatedAt(),
            appointment.getUpdatedAt()
        );
    }
}
