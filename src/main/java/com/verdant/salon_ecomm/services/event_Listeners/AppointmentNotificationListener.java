package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentBookedEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentCancelledEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentCompletedEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentDeletedEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentRescheduledEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentUpdatedEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentsBulkCancelledEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentsBulkDeletedEvent;
import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.entities.Appointment;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentNotificationListener {

    private static final List<AccountRole> STAFF_ROLES = List.of(
        AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.OWNER
    );

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Async
    @Retryable(
        retryFor = Exception.class,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentBooked(AppointmentBookedEvent event) {
        Appointment appointment = event.appointment();
        User customer = appointment.getUser();

        notificationService.create(new NotificationCreateDto(
            customer.getId(),
            NotificationType.APPOINTMENT_CREATED,
            "Appointment booked",
            appointment.getServiceName() + " booked for " + appointment.getScheduledAt() + ".",
            ReferenceType.APPOINTMENT,
            appointment.getId(),
            NotificationPriority.INFO,
            null,
            null
        ));

        notifyStaff(appointment, NotificationType.APPOINTMENT_CREATED, "New appointment",
            customer.getFullName() + " booked " + appointment.getServiceName()
                + " for " + appointment.getScheduledAt() + ".",
            null, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentRescheduled(AppointmentRescheduledEvent event) {
        Appointment appointment = event.appointment();

        if (!event.isSelfService()) {
            notificationService.create(new NotificationCreateDto(
                appointment.getUser().getId(),
                NotificationType.APPOINTMENT_RESCHEDULED,
                "Appointment rescheduled",
                appointment.getServiceName() + " moved to " + appointment.getScheduledAt() + ".",
                ReferenceType.APPOINTMENT,
                appointment.getId(),
                NotificationPriority.INFO,
                event.actor() != null ? event.actor().getId() : null,
                event.actor() != null ? event.actor().getFullName() : null
            ));
        }

        notifyStaff(appointment, NotificationType.APPOINTMENT_RESCHEDULED, "Appointment rescheduled",
            appointment.getAppointmentCode() + " moved to " + appointment.getScheduledAt() + ".",
            event.actor() != null ? event.actor().getId() : null,
            event.actor() != null ? event.actor().getFullName() : null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCancelled(AppointmentCancelledEvent event) {
        Appointment appointment = event.appointment();

        if (!event.isSelfService()) {
            notificationService.create(new NotificationCreateDto(
                appointment.getUser().getId(),
                NotificationType.APPOINTMENT_CANCELLED,
                "Appointment cancelled",
                appointment.getServiceName() + " (" + appointment.getAppointmentCode() + ") was cancelled.",
                ReferenceType.APPOINTMENT,
                appointment.getId(),
                NotificationPriority.WARNING,
                event.actor() != null ? event.actor().getId() : null,
                event.actor() != null ? event.actor().getFullName() : null
            ));
        }

        notifyStaff(appointment, NotificationType.APPOINTMENT_CANCELLED, "Appointment cancelled",
            appointment.getAppointmentCode() + " was cancelled.",
            event.actor() != null ? event.actor().getId() : null,
            event.actor() != null ? event.actor().getFullName() : null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCompleted(AppointmentCompletedEvent event) {
        Appointment appointment = event.appointment();

        // Staff performed the action themselves, so only the customer needs telling.
        notificationService.create(new NotificationCreateDto(
            appointment.getUser().getId(),
            NotificationType.APPOINTMENT_COMPLETED,
            "Appointment completed",
            appointment.getServiceName() + " is complete. We hope you enjoyed it!",
            ReferenceType.APPOINTMENT,
            appointment.getId(),
            NotificationPriority.INFO,
            event.actor() != null ? event.actor().getId() : null,
            event.actor() != null ? event.actor().getFullName() : null
        ));
    }

    @Async
    @Retryable(
        retryFor = Exception.class,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentUpdated(AppointmentUpdatedEvent event) {
        Appointment appointment = event.appointment();
        String fullSummary = event.changeSummary();

        // 1. Create a safe summary for the customer
        String safeSummary = buildCustomerSafeSummary(fullSummary);

        // 2. Build the customer message (use a generic fallback if all changes were internal)
        String customerMessage = safeSummary.isEmpty()
            ? "Your appointment " + appointment.getAppointmentCode() + " was updated."
            : "Your appointment " + appointment.getAppointmentCode() + " was updated: " + safeSummary;

        notificationService.create(new NotificationCreateDto(
            appointment.getUser().getId(),
            NotificationType.APPOINTMENT_UPDATED,
            "Appointment updated",
            customerMessage,
            ReferenceType.APPOINTMENT,
            appointment.getId(),
            NotificationPriority.INFO,
            event.actor() != null ? event.actor().getId() : null,
            event.actor() != null ? event.actor().getFullName() : null
        ));

        // 3. Keep the full raw summary for staff
        notifyStaff(appointment, NotificationType.APPOINTMENT_UPDATED, "Appointment updated",
            appointment.getAppointmentCode() + " updated: " + fullSummary,
            event.actor() != null ? event.actor().getId() : null,
            event.actor() != null ? event.actor().getFullName() : null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentDeleted(AppointmentDeletedEvent event) {
        Appointment appointment = event.appointment();

        notificationService.create(new NotificationCreateDto(
            appointment.getUser().getId(),
            NotificationType.APPOINTMENT_DELETED,
            "Appointment removed",
            appointment.getAppointmentCode() + " was removed by staff.",
            ReferenceType.APPOINTMENT,
            appointment.getId(),
            NotificationPriority.WARNING,
            event.actor() != null ? event.actor().getId() : null,
            event.actor() != null ? event.actor().getFullName() : null
        ));

        notifyStaff(appointment, NotificationType.APPOINTMENT_DELETED, "Appointment deleted",
            appointment.getAppointmentCode() + " deleted by staff.",
            event.actor() != null ? event.actor().getId() : null,
            event.actor() != null ? event.actor().getFullName() : null);
    }

    // ── Bulk operations: ONE staff/admin notification for the whole batch, ──────
    // ── plus an individual heads-up to each affected customer.               ──

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentsBulkCancelled(AppointmentsBulkCancelledEvent event) {
        UUID actorId = actorIdOrNull(event.actor());
        String actorName = actorNameOrNull(event.actor());

        List<Appointment> appointments = nullSafeAppointments(event);
        if (appointments.isEmpty()) return;

        for (Appointment appointment : appointments) {
            notificationService.create(new NotificationCreateDto(
                appointment.getUser().getId(),
                NotificationType.APPOINTMENT_CANCELLED,
                "Appointment cancelled",
                appointment.getServiceName() + " (" +
                    appointment.getAppointmentCode() + ") was cancelled.",
                ReferenceType.APPOINTMENT,
                appointment.getId(),
                NotificationPriority.WARNING,
                actorId,
                actorName
            ));
        }

        notifyStaffBulk(NotificationType.BULK_ACTION_PERFORMED, "Bulk appointment cancellation",
            appointments.size() + " appointments were cancelled" +
                    (actorName != null ? " by " + actorName : "") + ".",
            appointments.getFirst().getId(), actorId, actorName);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentsBulkDeleted(AppointmentsBulkDeletedEvent event) {
        UUID actorId = actorIdOrNull(event.actor());
        String actorName = actorNameOrNull(event.actor());

        List<Appointment> appointments = event.appointments();
        if (appointments == null || appointments.isEmpty()) return;

        for (Appointment appointment : appointments) {
            notificationService.create(new NotificationCreateDto(
                appointment.getUser().getId(),
                NotificationType.APPOINTMENT_DELETED,
                "Appointment removed",
                appointment.getAppointmentCode() + " was removed by staff.",
                ReferenceType.APPOINTMENT,
                appointment.getId(),
                NotificationPriority.WARNING,
                actorId,
                actorName
            ));
        }

        notifyStaffBulk(NotificationType.BULK_ACTION_PERFORMED, "Bulk appointment deletion",
            appointments.size() + " appointments were deleted" +
                    (actorName != null ? " by " + actorName : "") + ".",
            appointments.getFirst().getId(), actorId, actorName);
    }

    // ── Helpers ──────────────────────────────────────────

    private void notifyStaff(
        Appointment appointment, NotificationType type, String title, String message,
        UUID actorId, String actorName
    ) {
        notifyStaffBulk(type, title, message, appointment.getId(), actorId, actorName);
    }

    private void notifyStaffBulk(
        NotificationType type, String title, String message,
        UUID referenceAppointmentId, UUID actorId, String actorName
    ) {
        List<User> staff = userRepository.findByRoleIn(STAFF_ROLES);
        for (User staffMember : staff) {
            try{
                notificationService.create(new NotificationCreateDto(
                    staffMember.getId(),
                    type,
                    title,
                    message,
                    ReferenceType.APPOINTMENT,
                    referenceAppointmentId,
                    NotificationPriority.INFO,
                    actorId,
                    actorName
                ));
            } catch (Exception e) {
                log.error("Failed to notify staff member {}: {}", staffMember.getId(), e.getMessage(), e);
            }
        }
    }
    
    private String buildCustomerSafeSummary(String fullSummary) {
        if (fullSummary == null || fullSummary.isBlank()) {
            return "";
        }

        // Split by semicolon, trim whitespace, and filter out sensitive fields
        return java.util.Arrays.stream(fullSummary.split(";"))
            .map(String::trim)
            .filter(change -> {
                String lowerChange = change.toLowerCase();
                return !lowerChange.startsWith("customer") && !lowerChange.startsWith("notes");
            })
            .collect(java.util.stream.Collectors.joining(", "));
    }

    private UUID actorIdOrNull(User actor) {
        return actor != null ? actor.getId() : null;
    }

    private String actorNameOrNull(User actor) {
        return actor != null ? actor.getFullName() : null;
    }

    private List<Appointment> nullSafeAppointments(AppointmentsBulkCancelledEvent event) {
        List<Appointment> appointments = event.appointments();
        return appointments != null ? appointments : List.of();
    }
}
