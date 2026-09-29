package com.verdant.salon_ecomm.dtos.favorites.events;

import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.ItemType;

import java.util.UUID;

public record FavoriteToggledEvent(
    User user,
    ItemType targetType,
    UUID targetId,
    String targetName,
    boolean added
) {
}
