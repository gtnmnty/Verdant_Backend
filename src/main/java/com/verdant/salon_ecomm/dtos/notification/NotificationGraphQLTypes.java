package com.verdant.salon_ecomm.dtos.notification;

import java.util.List;
import java.util.UUID;

public record NotificationGraphQLTypes() {
    public record NotificationEdge(String cursor, NotificationResponseDto node) {}

    public record PageInfo(boolean hasNextPage, String endCursor) {}

    public record NotificationConnection(List<NotificationEdge> edges, PageInfo pageInfo, long unreadCount) {}

    public record MarkAsReadResult(List<UUID> updatedIds) {}

    public record DeleteNotificationsResult(List<UUID> deletedIds) {}
}

