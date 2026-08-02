package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.service.events.SalonServiceCreatedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceDeletedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceImageUpdatedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceUpdatedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServicesBulkDeletedEvent;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.utils.StaffNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SalonServiceNotificationListener {

    private final StaffNotifier staffNotifier;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalonServiceCreated(SalonServiceCreatedEvent event) {
        SalonService service = event.service();
        staffNotifier.notify(ReferenceType.SERVICE, service.getId(), NotificationType.SERVICE_ADDED,
            "New service added",
            "\"" + service.getName() + "\" was added to the catalog.",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalonServiceUpdated(SalonServiceUpdatedEvent event) {
        if (!event.hasChanges()) return;

        SalonService service = event.service();
        staffNotifier.notify(ReferenceType.SERVICE, service.getId(), NotificationType.SERVICE_ADDED,
            "New service added",
            "\"" + service.getName() + "\" was added to the catalog.",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalonServiceImageUpdated(SalonServiceImageUpdatedEvent event) {
        SalonService service = event.service();
        staffNotifier.notify(ReferenceType.SERVICE, service.getId(), NotificationType.SERVICE_IMAGE_UPDATED,
            "Service image updated",
            "Primary image for \"" + service.getName() + "\" was changed.",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalonServiceDeleted(SalonServiceDeletedEvent event) {
        SalonService service = event.service();
        staffNotifier.notify(ReferenceType.SERVICE, service.getId(), NotificationType.SERVICE_DELETED,
            "Service deleted",
            "\"" + service.getName() + "\" was removed from the catalog.",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalonServicesBulkDeleted(SalonServicesBulkDeletedEvent event) {
        if (event.services().isEmpty()) return;
        SalonService first = event.services().getFirst();
        staffNotifier.notify(ReferenceType.SERVICE, first.getId(), NotificationType.BULK_ACTION_PERFORMED,
            "Bulk service deletion",
            event.services().size() + " services were deleted"
                + (event.actor() != null ? " by " + event.actor().getFullName() : "") + ".",
            event.actor());
    }
}