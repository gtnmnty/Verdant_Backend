package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.stylists.events.StylistAssignedToServicesEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistCreatedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistDeletedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistImageUpdatedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistStatusChangedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistUpdatedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistsBulkDeletedEvent;
import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class StylistAuditListener {

    private final AuditLogService auditLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStylistCreated(StylistCreatedEvent event) {
        Stylist stylist = event.stylist();
        auditLogService.record(
            AuditEntityType.STYLIST,
            stylist.getId(),
            AuditActionType.CREATED,
            "Stylist \"" + stylist.getName() + "\" added",
            null,
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStylistUpdated(StylistUpdatedEvent event) {
        if (!event.hasChanges()) return;

        Stylist stylist = event.stylist();
        auditLogService.record(
            AuditEntityType.STYLIST,
            stylist.getId(),
            AuditActionType.UPDATED,
            "Stylist \"" + stylist.getName() + "\" updated",
            event.changeSummary(),
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStylistImageUpdated(StylistImageUpdatedEvent event) {
        Stylist stylist = event.stylist();
        auditLogService.record(
            AuditEntityType.STYLIST,
            stylist.getId(),
            AuditActionType.IMAGE_UPDATED,
            "Stylist \"" + stylist.getName() + "\" photo changed",
            null,
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStylistStatusChanged(StylistStatusChangedEvent event) {
        Stylist stylist = event.stylist();
        auditLogService.record(
            AuditEntityType.STYLIST,
            stylist.getId(),
            AuditActionType.STATUS_CHANGED,
            "Stylist \"" + stylist.getName() + "\" status changed",
            event.previousStatus() + " -> " + stylist.getStatus(),
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStylistAssignedToServices(StylistAssignedToServicesEvent event) {
        Stylist stylist = event.stylist();
        auditLogService.record(
            AuditEntityType.STYLIST,
            stylist.getId(),
            AuditActionType.ASSIGNED,
            "Stylist \"" + stylist.getName() + "\" service assignments changed",
            event.previousServiceCount() + " services -> " + event.newServiceCount() + " services",
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStylistDeleted(StylistDeletedEvent event) {
        Stylist stylist = event.stylist();
        auditLogService.record(
            AuditEntityType.STYLIST,
            stylist.getId(),
            AuditActionType.DELETED,
            "Stylist \"" + stylist.getName() + "\" deleted",
            null,
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStylistsBulkDeleted(StylistsBulkDeletedEvent event) {
        if (event.stylists().isEmpty()) return;
        int count = event.stylists().size();
        auditLogService.record(
            AuditEntityType.STYLIST,
            event.stylists().get(0).getId(),
            AuditActionType.BULK_DELETED,
            count + " stylists deleted",
            "Bulk deleted by staff",
            event.actor()
        );
    }
}
