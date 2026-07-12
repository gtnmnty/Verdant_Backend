package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.dtos.appointment.AdminAppointmentDto;
import com.verdant.salon_ecomm.dtos.appointment.CreateAppointmentInput;
import com.verdant.salon_ecomm.entities.*;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AppointmentMapper {

    public AdminAppointmentDto toAdminDto(Appointment appointment) {
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

    public Appointment toEntity(
        CreateAppointmentInput input, User user,
        SalonService service, Stylist stylist, Branch branch
    ) {
        Integer guests = input.guests();
        if ((guests != null && (guests < 1 || guests > Short.MAX_VALUE))) {
            throw new IllegalArgumentException("Guests must be between 1 and " + Short.MAX_VALUE);
        }

        return Appointment.builder()
            .user(user)
            .service(service)
            .serviceName(service.getName())
            .serviceType(input.serviceType())
            .priceSnapshot(service.getPrice())
            .stylist(stylist)
            .branch(branch)
            .scheduledAt(input.scheduledAt())
            .durationMinutes(service.getDurationMinutes())
            .guests(guests != null ? guests.shortValue() : (short) 1)
            .notes(input.notes())
            .status(AppointmentStatus.PENDING)
            .homeAddress(toHomeAddressMap(input.homeAddress()))
            .build();
        // appointmentCode is generated and set by the service, not here
    }

    private Map<String, Object> toHomeAddressMap(AddressInput input) {
        if (input == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("line1", input.line1());
        map.put("line2", input.line2());
        map.put("city", input.city());
        map.put("state", input.state());
        map.put("postal", input.postal());
        map.put("country", input.country());
        return map;
    }
}