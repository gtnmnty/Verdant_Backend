package com.verdant.salon_ecomm.services.events.listeners.appointment;

import com.verdant.salon_ecomm.dtos.appointment.events.*;
import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.models.entities.Appointment;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.services.NotificationService;
import com.verdant.salon_ecomm.utils.StaffNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentNotificationListener {

    private final NotificationService notificationService;
    private final StaffNotifier staffNotifier;

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

        String staffMessage = event.isSelfService()
            ? customer.getFullName() + " booked " + appointment.getServiceName()
                + " for " + appointment.getScheduledAt() + "."
            : appointment.getServiceName() + " booked for " + customer.getFullName()
                + " by staff for " + appointment.getScheduledAt() + ".";
        notifyStaff(appointment, NotificationType.APPOINTMENT_CREATED, "New appointment", staffMessage,
            null, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentApproved(AppointmentApprovedEvent event) {
        Appointment appointment = event.appointment();

        notificationService.create(new NotificationCreateDto(
            appointment.getUser().getId(),
            NotificationType.APPOINTMENT_APPROVED,
            "Appointment confirmed",
            appointment.getServiceName() + " has been confirmed for " + appointment.getScheduledAt() + ".",
            ReferenceType.APPOINTMENT,
            appointment.getId(),
            NotificationPriority.INFO,
            event.actor() != null ? event.actor().getId() : null,
            event.actor() != null ? event.actor().getFullName() : null
        ));
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

        // 1. Create a safe summary for the customer (M-34: built from the structured change list)
        String safeSummary = buildCustomerSafeSummary(event.changes());

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

    // M-38: delegates to StaffNotifier (active staff only, one transaction, per-recipient fallback).
    private void notifyStaffBulk(
        NotificationType type, String title, String message,
        UUID referenceAppointmentId, UUID actorId, String actorName
    ) {
        staffNotifier.notify(ReferenceType.APPOINTMENT, referenceAppointmentId, type, title, message, actorId, actorName);
    }

    // M-34: fields a customer may see in "your appointment was updated: ...". An allow-list, so a field added
    // later (or an internal one like notes or customer) stays hidden until someone decides it is safe.
    private static final Set<String> CUSTOMER_VISIBLE_FIELDS = Set.of(
        "service", "stylist", "scheduledAt", "guests", "serviceType", "branch", "homeAddress"
    );

    // M-34: was built by splitting the joined summary on ";" and dropping parts that START with "notes".
    // A note that itself contained ";" (e.g. "VIP; client owes $50") split into extra parts, and the text
    // after the semicolon was sent to the customer. Filtering the structured list by field name can't leak it.
    private String buildCustomerSafeSummary(List<AppointmentUpdatedEvent.FieldChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return "";
        }
        return changes.stream()
            .filter(change -> CUSTOMER_VISIBLE_FIELDS.contains(change.field()))
            .map(AppointmentUpdatedEvent.FieldChange::toString)
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
