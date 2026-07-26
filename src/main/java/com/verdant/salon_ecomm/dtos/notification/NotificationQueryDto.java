package com.verdant.salon_ecomm.dtos.notification;

import com.verdant.salon_ecomm.models.enums.notification.NotificationReadFilter;
import com.verdant.salon_ecomm.models.enums.notification.NotificationSortField;
import com.verdant.salon_ecomm.models.enums.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.SortDirection;

import java.util.List;

public record NotificationQueryDto(
    NotificationReadFilter readFilter,
    String search,
    List<NotificationType> types,
    NotificationSortField sortField,
    SortDirection sortDirection,
    Integer page,
    Integer size
) {}
