package com.verdant.salon_ecomm.dtos.notification;

import java.util.List;
import java.util.UUID;

public record NotificationBulkActionDto(
    List<UUID> ids
) {}
