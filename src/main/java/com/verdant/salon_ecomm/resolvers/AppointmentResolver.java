package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.appointment.*;
import com.verdant.salon_ecomm.dtos.Address;
import com.verdant.salon_ecomm.entities.Appointment;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.appointments.*;
import com.verdant.salon_ecomm.services.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class AppointmentResolver {

    private final AppointmentService appointmentService;

    // ---------- Queries ----------

    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    public AppointmentPage myAppointments(
        @Argument AppointmentClientFilter status,
        @Argument AppointmentTimeFrame timeframe,
        @Argument String search,
        @Argument AppointmentClientSort sort,
        @Argument int page,
        @Argument int pageSize,
        @AuthenticationPrincipal User principal
    ) {
        return appointmentService.getMyAppointments(principal.getId(), status, timeframe, search, sort, page, pageSize);
    }

    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','ADMIN','OWNER')")
    @QueryMapping
    public Appointment appointment(@Argument UUID id, @AuthenticationPrincipal User principal) {
        boolean isAdmin = hasAdminRole(principal);
        return appointmentService.getAppointmentById(id, principal.getId(), isAdmin);
    }

    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','ADMIN','OWNER')")
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

    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','ADMIN','OWNER')")
    @QueryMapping
    public AdminAppointmentDto adminAppointment(
        @Argument UUID id, @Argument UUID currentUserId, @Argument boolean isAdmin
    ) {
        return appointmentService.getAdminAppointmentById(id,  currentUserId, isAdmin);
    }

    @PreAuthorize("isAuthenticated()")
    @QueryMapping
    public AppointmentStatusCounts appointmentStatusCounts(@AuthenticationPrincipal User principal) {
        UUID userId = hasAdminRole(principal) ? null : principal.getId();
        return appointmentService.getAppointmentStatusCounts(userId);
    }

    // ---------- Mutations ----------

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    public Appointment bookAppointment(
        @Argument("input") CreateAppointmentInput input,
        @AuthenticationPrincipal User principal
    ) {
        return appointmentService.bookAppointment(input, principal.getId());
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    public Appointment rescheduleAppointment(
        @Argument UUID id,
        @Argument OffsetDateTime newScheduledAt,
        @AuthenticationPrincipal User principal
    ) {
        return appointmentService.rescheduleAppointment(id, newScheduledAt, principal.getId(), hasAdminRole(principal));
    }

    @PreAuthorize("isAuthenticated()")
    @MutationMapping
    public Appointment cancelAppointment(@Argument UUID id, @AuthenticationPrincipal User principal) {
        return appointmentService.cancelAppointment(id, principal.getId(), hasAdminRole(principal));
    }

    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','ADMIN','OWNER')")
    @MutationMapping
    public Appointment completeAppointment(
        @Argument UUID id, @Argument UUID currentUserId, @Argument boolean isAdmin
    ) {
        return appointmentService.completeAppointment(id, currentUserId, isAdmin);
    }

    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','ADMIN','OWNER')")
    @MutationMapping
    public List<Appointment> cancelAppointments(@Argument List<UUID> ids) {
        return appointmentService.cancelAppointments(ids);
    }

    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','ADMIN','OWNER')")
    @MutationMapping
    public Appointment updateAppointmentRequest(
        @Argument UUID id, @Argument("input")  UpdateAppointmentInput input,
        @Argument UUID currentUserId, @Argument boolean isAdmin
    ) {
        return appointmentService.updateAppointmentRequest(id, input, currentUserId, isAdmin);
    }

    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','ADMIN','OWNER')")
    @MutationMapping
    public Appointment deleteAppointment(@Argument UUID id) {
        return appointmentService.deleteAppointment(id);
    }

    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','ADMIN','OWNER')")
    @MutationMapping
    public List<Appointment> deleteAppointments(@Argument List<UUID> ids) {
        return appointmentService.deleteAppointments(ids);
    }

    // ---------- Field resolvers ----------

    @SchemaMapping(typeName = "Appointment", field = "branch")
    public String branch(Appointment appointment) {
        return appointment.getBranch() != null ? appointment.getBranch().getName() : null;
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

    // ---------- Helpers ----------

    private boolean hasAdminRole(User principal) {
        return principal.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}