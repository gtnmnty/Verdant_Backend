package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.dtos.user.events.UserPasswordChangedEvent;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserNotificationListener {

    // Registration, profile edits, and self-deletion are audit-only (the person
    // already knows they did it, and staff don't need to hear about every
    // customer profile tweak). Password change gets a notification because it's
    // a security-relevant event worth a heads-up in case it wasn't the user.
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserPasswordChanged(UserPasswordChangedEvent event) {
        notificationService.create(new NotificationCreateDto(
            event.user().getId(),
            NotificationType.PASSWORD_CHANGED,
            "Password changed",
            "Your password was changed. If this wasn't you, contact support immediately.",
            ReferenceType.USER,
            event.user().getId(),
            NotificationPriority.WARNING,
            event.user().getId(),
            event.user().getFullName()
        ));
    }
}
