package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentBookedEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentCancelledEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentCompletedEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentDeletedEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentRescheduledEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentUpdatedEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentsBulkCancelledEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentsBulkDeletedEvent;
import com.verdant.salon_ecomm.entities.Appointment;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AppointmentAuditListener {

    private final AuditLogService auditLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentBooked(AppointmentBookedEvent event) {
        Appointment appointment = event.appointment();
        auditLogService.recordSelfService(
            AuditEntityType.APPOINTMENT,
            appointment.getId(),
            AuditActionType.BOOKED,
            "Appointment " + appointment.getAppointmentCode() + " booked",
            appointment.getServiceName() + " booked by " + appointment.getUser().getFullName()
                + " for " + appointment.getScheduledAt()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentRescheduled(AppointmentRescheduledEvent event) {
        Appointment appointment = event.appointment();
        String title = "Appointment " + appointment.getAppointmentCode() + " rescheduled";
        String detail = event.previousScheduledAt() + " -> " + appointment.getScheduledAt();

        if (event.isSelfService()) {
            auditLogService.recordSelfService(
                AuditEntityType.APPOINTMENT, appointment.getId(), AuditActionType.RESCHEDULED, title, detail
            );
        } else {
            auditLogService.record(
                AuditEntityType.APPOINTMENT, appointment.getId(), AuditActionType.RESCHEDULED,
                title, detail, event.actor()
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCancelled(AppointmentCancelledEvent event) {
        Appointment appointment = event.appointment();
        String title = "Appointment " + appointment.getAppointmentCode() + " cancelled";

        if (event.isSelfService()) {
            auditLogService.recordSelfService(
                AuditEntityType.APPOINTMENT, appointment.getId(), AuditActionType.CANCELLED,
                title, "Cancelled by customer " + appointment.getUser().getFullName()
            );
        } else {
            auditLogService.record(
                AuditEntityType.APPOINTMENT, appointment.getId(), AuditActionType.CANCELLED,
                title, "Cancelled by staff", event.actor()
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCompleted(AppointmentCompletedEvent event) {
        Appointment appointment = event.appointment();
        auditLogService.record(
            AuditEntityType.APPOINTMENT,
            appointment.getId(),
            AuditActionType.COMPLETED,
            "Appointment " + appointment.getAppointmentCode() + " completed",
            appointment.getServiceName() + " for " + appointment.getUser().getFullName() + " marked complete",
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentUpdated(AppointmentUpdatedEvent event) {
        Appointment appointment = event.appointment();
        auditLogService.record(
            AuditEntityType.APPOINTMENT,
            appointment.getId(),
            AuditActionType.UPDATED,
            "Appointment " + appointment.getAppointmentCode() + " updated",
            event.changeSummary(),
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentDeleted(AppointmentDeletedEvent event) {
        Appointment appointment = event.appointment();
        auditLogService.record(
            AuditEntityType.APPOINTMENT,
            appointment.getId(),
            AuditActionType.DELETED,
            "Appointment " + appointment.getAppointmentCode() + " deleted",
            null,
            event.actor()
        );
    }

    // Bulk operations get ONE log entry summarizing the whole batch, not one per appointment.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentsBulkCancelled(AppointmentsBulkCancelledEvent event) {
        int count = event.appointments().size();
        auditLogService.record(
            AuditEntityType.APPOINTMENT,
            event.appointments().getFirst().getId(),
            AuditActionType.BULK_CANCELLED,
            count + " appointments cancelled",
            "Bulk cancelled by staff",
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentsBulkDeleted(AppointmentsBulkDeletedEvent event) {
        int count = event.appointments().size();
        auditLogService.record(
            AuditEntityType.APPOINTMENT,
            event.appointments().getFirst().getId(),
            AuditActionType.BULK_DELETED,
            count + " appointments deleted",
            "Bulk deleted by staff",
            event.actor()
        );
    }
}
