package com.verdant.salon_ecomm.dtos.notification;

import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponseDto(
    UUID id,
    NotificationType type,
    String title,
    String message,
    ReferenceType referenceType,
    UUID referenceId,
    Boolean isRead,
    OffsetDateTime readAt,
    NotificationPriority priority,
    UUID actorId,
    String actorName,
    OffsetDateTime createdAt
) {}
