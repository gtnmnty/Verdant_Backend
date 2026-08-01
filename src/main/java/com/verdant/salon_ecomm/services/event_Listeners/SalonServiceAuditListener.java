package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.service.events.SalonServiceCreatedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceDeletedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceImageUpdatedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceUpdatedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServicesBulkDeletedEvent;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SalonServiceAuditListener {

    private final AuditLogService auditLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalonServiceCreated(SalonServiceCreatedEvent event) {
        SalonService service = event.service();
        auditLogService.record(
            AuditEntityType.SALON_SERVICE,
            service.getId(),
            AuditActionType.CREATED,
            "Service \"" + service.getName() + "\" created",
            null,
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalonServiceUpdated(SalonServiceUpdatedEvent event) {
        if (!event.hasChanges()) return;

        SalonService service = event.service();
        auditLogService.record(
            AuditEntityType.SALON_SERVICE,
            service.getId(),
            AuditActionType.UPDATED,
            "Service \"" + service.getName() + "\" updated",
            event.changeSummary(),
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalonServiceImageUpdated(SalonServiceImageUpdatedEvent event) {
        SalonService service = event.service();
        auditLogService.record(
            AuditEntityType.SALON_SERVICE,
            service.getId(),
            AuditActionType.IMAGE_UPDATED,
            "Service \"" + service.getName() + "\" primary image changed",
            null,
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalonServiceDeleted(SalonServiceDeletedEvent event) {
        SalonService service = event.service();
        auditLogService.record(
            AuditEntityType.SALON_SERVICE,
            service.getId(),
            AuditActionType.DELETED,
            "Service \"" + service.getName() + "\" deleted",
            null,
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalonServicesBulkDeleted(SalonServicesBulkDeletedEvent event) {
        if (event.services().isEmpty()) return;
        int count = event.services().size();
        auditLogService.record(
            AuditEntityType.SALON_SERVICE,
            event.services().get(0).getId(),
            AuditActionType.BULK_DELETED,
            count + " services deleted",
            "Bulk deleted by staff",
            event.actor()
        );
    }
}
