package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceCreatedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceDeletedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceImageUpdatedEvent;
import com.verdant.salon_ecomm.dtos.service.events.SalonServiceUpdatedEvent;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.AccountRole;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SalonServiceNotificationListener {

    // Catalog changes are a staff/admin concern only — customers aren't notified
    // when a service's details, image, or availability change.
    private static final List<AccountRole> STAFF_ROLES = List.of(
        AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.OWNER
    );

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalonServiceCreated(SalonServiceCreatedEvent event) {
        SalonService service = event.service();
        notifyStaff(service, NotificationType.SERVICE_ADDED, "New service added",
            "\"" + service.getName() + "\" was added to the catalog.",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalonServiceUpdated(SalonServiceUpdatedEvent event) {
        if (!event.hasChanges()) return;

        SalonService service = event.service();
        notifyStaff(service, NotificationType.SERVICE_UPDATED, "Service updated",
            "\"" + service.getName() + "\" was updated: " + event.changeSummary(),
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalonServiceImageUpdated(SalonServiceImageUpdatedEvent event) {
        SalonService service = event.service();
        notifyStaff(service, NotificationType.SERVICE_IMAGE_UPDATED, "Service image updated",
            "Primary image for \"" + service.getName() + "\" was changed.",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalonServiceDeleted(SalonServiceDeletedEvent event) {
        SalonService service = event.service();
        notifyStaff(service, NotificationType.SERVICE_DELETED, "Service deleted",
            "\"" + service.getName() + "\" was removed from the catalog.",
            event.actor());
    }

    // ── Helpers ──────────────────────────────────────────

    private void notifyStaff(SalonService service, NotificationType type, String title, String message, User actor) {
        UUID actorId = actor != null ? actor.getId() : null;
        String actorName = actor != null ? actor.getFullName() : null;

        List<User> staff = userRepository.findByRoleIn(STAFF_ROLES);
        for (User staffMember : staff) {
            if (actorId != null && actorId.equals(staffMember.getId())) continue;
            notificationService.create(new NotificationCreateDto(
                staffMember.getId(),
                type,
                title,
                message,
                ReferenceType.SERVICE,
                service.getId(),
                NotificationPriority.INFO,
                actorId,
                actorName
            ));
        }
    }
}
