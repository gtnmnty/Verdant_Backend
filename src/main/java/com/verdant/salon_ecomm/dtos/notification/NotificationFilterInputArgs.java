package com.verdant.salon_ecomm.dtos.notification;

import com.verdant.salon_ecomm.models.enums.notification.NotificationReadFilter;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;

import java.util.List;

public record NotificationFilterInputArgs(
    NotificationReadFilter readFilter,
    String search,
    List<NotificationType> types
) {}
