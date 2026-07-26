package com.verdant.salon_ecomm.dtos.notification;

import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;

import java.util.UUID;

public record NotificationCreateDto(
    UUID userId,
    NotificationType type,
    String title,
    String message,
    ReferenceType referenceType,
    UUID referenceId,
    NotificationPriority priority,
    UUID actorId,
    String actorName
) {}
