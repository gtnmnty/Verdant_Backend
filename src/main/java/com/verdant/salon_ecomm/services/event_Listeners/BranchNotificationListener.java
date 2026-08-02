package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.dtos.branch.events.BranchesBulkDeletedEvent;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
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
public class BranchNotificationListener {

    private static final List<AccountRole> STAFF_ROLES = List.of(
        AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.OWNER
    );

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBranchesBulkDeleted(BranchesBulkDeletedEvent event) {
        if (event.branches().isEmpty()) return;

        UUID actorId = event.actor() != null ? event.actor().getId() : null;
        String actorName = event.actor() != null ? event.actor().getFullName() : null;
        String message = event.branches().size() + " branches were deleted"
            + (actorName != null ? " by " + actorName : "") + ".";

        List<User> staff = userRepository.findByRoleIn(STAFF_ROLES);
        for (User staffMember : staff) {
            notificationService.create(new NotificationCreateDto(
                staffMember.getId(),
                NotificationType.BULK_ACTION_PERFORMED,
                "Bulk branch deletion",
                message,
                ReferenceType.BRANCH,
                event.branches().getFirst().getId(),
                NotificationPriority.INFO,
                actorId,
                actorName
            ));
        }
    }
}
