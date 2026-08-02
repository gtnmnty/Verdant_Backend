package com.verdant.salon_ecomm.utils;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.AccountRole;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StaffNotifier {

    private static final List<AccountRole> STAFF_ROLES = List.of(
        AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.OWNER
    );

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public void notify(
        ReferenceType referenceType,
        UUID referenceId, NotificationType type,
        String title, String message, User actor
    ) {
        UUID actorId = actor != null ? actor.getId() : null;
        String actorName = actor != null ? actor.getFullName() : null;

        List<User> staff = userRepository.findByRoleIn(STAFF_ROLES);
        for (User staffMember : staff) {
            notificationService.create(new NotificationCreateDto(
                staffMember.getId(),
                type,
                title,
                message,
                referenceType,
                referenceId,
                NotificationPriority.INFO,
                actorId,
                actorName
            ));
        }
    }
}
