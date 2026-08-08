package com.verdant.salon_ecomm.dtos.notification;

import com.verdant.salon_ecomm.models.enums.notification.NotificationReadFilter;
import com.verdant.salon_ecomm.models.enums.notification.NotificationSortField;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.SortDirection;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record NotificationQueryDto(
    NotificationReadFilter readFilter,
    String search,
    List<NotificationType> types,
    NotificationSortField sortField,
    SortDirection sortDirection,
    OffsetDateTime cursorCreatedAt,
    UUID cursorId,
    Integer size
) {}
