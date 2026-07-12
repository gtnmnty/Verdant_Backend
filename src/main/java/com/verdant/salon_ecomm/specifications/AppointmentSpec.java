package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.entities.Appointment;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentClientFilter;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentServiceType;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.UUID;

public class AppointmentSpec {

    public static Specification<Appointment> filterAdminAppointments(
        AppointmentStatus status, UUID stylistId, String branchName,
        AppointmentServiceType serviceType, String search
    ) {
        return Specification.allOf(
            hasStatus(status),
            hasStylist(stylistId),
            hasBranch(branchName),
            hasServiceType(serviceType),
            matchesSearch(search)
        );
    }

    public static Specification<Appointment> filterMyAppointments(
        UUID userId, AppointmentClientFilter clientFilter,
        String search, OffsetDateTime windowStart, OffsetDateTime windowEnd, boolean archived
    ) {
        return Specification.allOf(
            belongsToUser(userId),
            hasClientFilter(clientFilter),
            matchesSearch(search),
            archived ? outsideWindow(windowStart, windowEnd) : withinWindow(windowStart, windowEnd)
        );
    }

    public static Specification<Appointment> hasStatus(AppointmentStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Appointment> matchesSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("appointmentCode")), pattern),
            cb.like(cb.lower(root.get("serviceName")), pattern)
        );
    }

    public static Specification<Appointment> hasStylist(UUID stylistId) {
        if (stylistId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("stylist").get("id"), stylistId);
    }

    public static Specification<Appointment> hasServiceType(AppointmentServiceType serviceType) {
        if (serviceType == null) return null;
        return (root, query, cb) -> cb.equal(root.get("serviceType"), serviceType);
    }

    public static Specification<Appointment> hasBranch(String branchName) {
        if (branchName == null || branchName.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("branch").get("name"), branchName);
    }

    public static Specification<Appointment> belongsToUser(UUID userId) {
        if (userId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Appointment> hasClientFilter(AppointmentClientFilter clientFilter) {
        if (clientFilter == null || clientFilter == AppointmentClientFilter.ALL) return null;
        return hasStatus(AppointmentStatus.valueOf(clientFilter.name()));
    }

    public static Specification<Appointment> withinWindow(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) return null;
        return (root, query, cb) -> cb.between(root.get("scheduledAt"), start, end);
    }

    public static Specification<Appointment> outsideWindow(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) return null;
        return (root, query, cb) -> cb.or(
            cb.lessThan(root.get("scheduledAt"), start),
            cb.greaterThan(root.get("scheduledAt"), end)
        );
    }

}