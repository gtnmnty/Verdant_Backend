package com.verdant.salon_ecomm.dtos.notification;

import java.util.List;

public record NotificationGroupedConnection(
    List<NotificationGroupDto> groups,
    boolean hasNextPage,
    String endCursor
) {}
