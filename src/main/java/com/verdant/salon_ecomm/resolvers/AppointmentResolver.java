package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.appointment.*;
import com.verdant.salon_ecomm.dtos.Address;
import com.verdant.salon_ecomm.entities.Appointment;
import com.verdant.salon_ecomm.models.enums.appointments.*;
import com.verdant.salon_ecomm.services.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class AppointmentResolver {

    private final AppointmentService appointmentService;

    // NOTE: every method below that needs "the current user" uses a placeholder
    // getCurrentUserId()/isAdmin() — wire these to your actual auth/security
    // context (Spring Security principal, JWT claims, whatever you're using).

    @QueryMapping
    public AppointmentPage myAppointments(
        @Argument AppointmentClientFilter status,
        @Argument AppointmentTimeFrame timeframe,
        @Argument String search,
        @Argument AppointmentClientSort sort,
        @Argument int page,
        @Argument int pageSize
    ) {
        UUID userId = getCurrentUserId();
        return appointmentService.getMyAppointments(userId, status, timeframe, search, sort, page, pageSize);
    }

    @QueryMapping
    public Appointment appointment(@Argument UUID id) {
        return appointmentService.getAppointmentById(id);
    }

    @QueryMapping
    public AdminAppointmentPage adminAppointments(
        @Argument AppointmentStatus status,
        @Argument UUID stylistId,
        @Argument String branch,
        @Argument AppointmentServiceType serviceType,
        @Argument String search,
        @Argument AdminAppointmentSort sort,
        @Argument int page,
        @Argument int pageSize
    ) {
        return appointmentService.getAdminAppointments(status, stylistId, branch, serviceType, search, sort, page, pageSize);
    }

    @QueryMapping
    public AdminAppointmentDto adminAppointment(@Argument UUID id) {
        return appointmentService.getAdminAppointmentById(id);
    }

    @QueryMapping
    public AppointmentStatusCounts appointmentStatusCounts() {
        UUID userId = isCurrentUserAdmin() ? null : getCurrentUserId();
        return appointmentService.getAppointmentStatusCounts(userId);
    }

    @MutationMapping
    public Appointment bookAppointment(
        @Argument("input") CreateAppointmentInput input,
        @Argument UUID currentUserId
    ) {
        return appointmentService.bookAppointment(input, currentUserId);
    }

    @MutationMapping
    public Appointment rescheduleAppointment(
        @Argument UUID id, @Argument OffsetDateTime newScheduledAt,
        @Argument UUID currentUserId, @Argument boolean isAdmin
    ) {
        return appointmentService.rescheduleAppointment(id, newScheduledAt, currentUserId, isAdmin);
    }

    @MutationMapping
    public Appointment cancelAppointment(
        @Argument UUID id, @Argument UUID currentUserId, @Argument boolean isAdmin
    ) {
        return appointmentService.cancelAppointment(id, currentUserId, isAdmin);
    }

    @MutationMapping
    public Appointment completeAppointment(@Argument UUID id) {
        return appointmentService.completeAppointment(id);
    }

    @MutationMapping
    public List<Appointment> cancelAppointments(@Argument List<UUID> ids) {
        return appointmentService.cancelAppointments(ids);
    }

    @MutationMapping
    public Appointment updateAppointmentRequest(@Argument UUID id, @Argument("input") UpdateAppointmentInput input) {
        return appointmentService.updateAppointmentRequest(id, input);
    }

    @MutationMapping
    public Appointment deleteAppointment(@Argument UUID id) {
        return appointmentService.deleteAppointment(id);
    }

    @MutationMapping
    public List<Appointment> deleteAppointments(@Argument List<UUID> ids) {
        return appointmentService.deleteAppointments(ids);
    }

    // Appointment.branch is a Branch relation on the entity, but the schema
    // field is `branch: String` — this flattens it for the client-facing type.
    // (AdminAppointmentDto already does this same flattening inside the mapper.)
    @SchemaMapping(typeName = "Appointment", field = "branch")
    public String branch(Appointment appointment) {
        return appointment.getBranch() != null ? appointment.getBranch().getName() : null;
    }

    private UUID getCurrentUserId() {
        throw new UnsupportedOperationException("TODO: pull the authenticated user's ID from your security context");
    }

    private boolean isCurrentUserAdmin() {
        throw new UnsupportedOperationException("TODO: check the authenticated user's role from your security context");
    }

    @SchemaMapping(typeName = "Appointment", field = "homeAddress")
    public Address homeAddress(Appointment appointment) {
        Map<String, Object> map = appointment.getHomeAddress();
        if (map == null) return null;
        return new Address(
            (String) map.get("line1"),
            (String) map.get("line2"),
            (String) map.get("city"),
            (String) map.get("state"),
            (String) map.get("postal"),
            (String) map.get("country")
        );
    }
}