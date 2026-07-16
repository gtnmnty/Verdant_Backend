package com.verdant.salon_ecomm.dtos.reviews;

import com.verdant.salon_ecomm.models.enums.ItemType;

import java.util.UUID;

public record ReviewTargetKey(
    ItemType itemType, UUID targetId
) {}
