package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistAssignedToServicesEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistCreatedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistDeletedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistImageUpdatedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistStatusChangedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistUpdatedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistsBulkDeletedEvent;
import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.AccountRole;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.models.enums.stylists.StylistAccountStatus;
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
public class StylistNotificationListener {

    // HR/staff-roster changes are a staff/admin concern only.
    private static final List<AccountRole> STAFF_ROLES = List.of(
        AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.OWNER
    );

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStylistCreated(StylistCreatedEvent event) {
        Stylist stylist = event.stylist();
        notifyStaff(stylist, NotificationType.STYLIST_ADDED, "New stylist added",
            "\"" + stylist.getName() + "\" was added to the team.",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStylistUpdated(StylistUpdatedEvent event) {
        if (!event.hasChanges()) return;

        Stylist stylist = event.stylist();
        notifyStaff(stylist, NotificationType.STYLIST_UPDATED, "Stylist updated",
            "\"" + stylist.getName() + "\" was updated: " + event.changeSummary(),
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStylistImageUpdated(StylistImageUpdatedEvent event) {
        Stylist stylist = event.stylist();
        notifyStaff(stylist, NotificationType.STYLIST_IMAGE_UPDATED, "Stylist photo updated",
            "Photo for \"" + stylist.getName() + "\" was changed.",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStylistStatusChanged(StylistStatusChangedEvent event) {
        Stylist stylist = event.stylist();
        NotificationType type = stylist.getStatus() == StylistAccountStatus.ACTIVE
            ? NotificationType.STYLIST_UPDATED
            : NotificationType.STYLIST_DEACTIVATED;

        notifyStaff(stylist, type, "Stylist status changed",
            "\"" + stylist.getName() + "\" is now " + stylist.getStatus().name().toLowerCase() + ".",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStylistAssignedToServices(StylistAssignedToServicesEvent event) {
        Stylist stylist = event.stylist();
        notifyStaff(stylist, NotificationType.STYLIST_ASSIGNED, "Stylist assignments updated",
            "\"" + stylist.getName() + "\" is now assigned to " + event.newServiceCount() + " service(s).",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStylistDeleted(StylistDeletedEvent event) {
        Stylist stylist = event.stylist();
        notifyStaff(stylist, NotificationType.STYLIST_DELETED, "Stylist deleted",
            "\"" + stylist.getName() + "\" was removed from the team.",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStylistsBulkDeleted(StylistsBulkDeletedEvent event) {
        if (event.stylists().isEmpty()) return;
        notifyStaff(event.stylists().get(0), NotificationType.BULK_ACTION_PERFORMED, "Bulk stylist deletion",
            event.stylists().size() + " stylists were deleted"
                + (event.actor() != null ? " by " + event.actor().getFullName() : "") + ".",
            event.actor());
    }

    // ── Helpers ──────────────────────────────────────────

    private void notifyStaff(Stylist stylist, NotificationType type, String title, String message, User actor) {
        UUID actorId = actor != null ? actor.getId() : null;
        String actorName = actor != null ? actor.getFullName() : null;

        List<User> staff = userRepository.findByRoleIn(STAFF_ROLES);
        for (User staffMember : staff) {
            notificationService.create(new NotificationCreateDto(
                staffMember.getId(),
                type,
                title,
                message,
                ReferenceType.STYLIST,
                stylist.getId(),
                NotificationPriority.INFO,
                actorId,
                actorName
            ));
        }
    }
}
