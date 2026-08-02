package com.verdant.salon_ecomm.utils;

import com.verdant.salon_ecomm.entities.Favorite;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.FavoriteRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FavoriteBatchUtils {

    public static <T> Map<T, Boolean> resolveFavorited(
        List<T> items, Function<T, UUID> idExtractor,
        UUID userId, ItemType itemType, FavoriteRepository favoriteRepository
    ) {
        if (userId == null) {
            return items.stream().collect(Collectors.toMap(item -> item, item -> false));
        }

        List<UUID> ids = items.stream().map(idExtractor).toList();

        Set<UUID> favoritedIds = favoriteRepository
            .findByUserIdAndTargetIdInAndTargetType(userId, ids, itemType)
            .stream()
            .map(Favorite::getTargetId)
            .collect(Collectors.toSet());

        return items.stream()
            .collect(Collectors.toMap(item -> item, item -> favoritedIds.contains(idExtractor.apply(item))));
    }
}