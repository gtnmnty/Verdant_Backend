package com.verdant.salon_ecomm.utils;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// M-38: the ONE way a listener notifies staff. Before, eight listeners (account, appointment, branch, gift
// card, order, product, promo code, review) each had their own copy of STAFF_ROLES and a notify loop, and
// they had drifted apart:
//   - some isolated per-recipient failures and some didn't, so one bad row stopped everyone after it
//   - findByRoleIn ignored the account status, so suspended, banned and deleted staff were notified
//   - each recipient cost its own REQUIRES_NEW transaction on the request thread
// Flow: load the ids of ACTIVE staff -> insert every notification in one transaction
// (NotificationService.createForUsers) -> if that fails, retry one recipient at a time, each in its own
// try/catch, so a single bad row can't stop the rest.
// Never throws: a failed staff notification must not fail the listener (it runs after the commit, so the
// change itself is already saved).
@Slf4j
@Component
@RequiredArgsConstructor
public class StaffNotifier {

    // Everyone who works the admin side and should hear about business events.
    public static final List<AccountRole> STAFF_ROLES = List.of(
        AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.OWNER
    );

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public void notify(
        ReferenceType referenceType,
        UUID referenceId, NotificationType type,
        String title, String message, User actor
    ) {
        notify(referenceType, referenceId, type, title, message,
            actor != null ? actor.getId() : null,
            actor != null ? actor.getFullName() : null);
    }

    // For listeners that only have the actor's id and name (e.g. appointment bulk events).
    public void notify(
        ReferenceType referenceType,
        UUID referenceId, NotificationType type,
        String title, String message, UUID actorId, String actorName
    ) {
        List<UUID> recipients;
        try {
            recipients = userRepository.findIdsByRoleInAndStatus(STAFF_ROLES, AccountStatus.ACTIVE);
        } catch (RuntimeException e) {
            log.error("Could not load staff for a {} notification; none sent", type, e);
            return;
        }
        if (recipients.isEmpty()) return;

        NotificationCreateDto template = new NotificationCreateDto(
            null, type, title, message, referenceType, referenceId,
            NotificationPriority.INFO, actorId, actorName
        );

        try {
            notificationService.createForUsers(recipients, template);
        } catch (RuntimeException bulkFailure) {
            log.warn("Bulk {} notification for {} staff failed; retrying one by one",
                type, recipients.size(), bulkFailure);
            for (UUID recipientId : recipients) {
                try {
                    notificationService.create(new NotificationCreateDto(
                        recipientId, template.type(), template.title(), template.message(),
                        template.referenceType(), template.referenceId(), template.priority(),
                        template.actorId(), template.actorName()
                    ));
                } catch (RuntimeException e) {
                    log.error("Failed to notify staff member {} ({})", recipientId, type, e);
                }
            }
        }
    }
}
