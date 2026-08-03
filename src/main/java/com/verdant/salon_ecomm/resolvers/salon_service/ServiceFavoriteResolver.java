package com.verdant.salon_ecomm.resolvers.salon_service;

import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.FavoriteRepository;
import graphql.GraphQLContext;
import lombok.RequiredArgsConstructor;

import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.verdant.salon_ecomm.utils.FavoriteBatchUtils.resolveFavorited;

@Controller
@RequiredArgsConstructor
public class ServiceFavoriteResolver {

    private final FavoriteRepository favoriteRepository;

    @BatchMapping(typeName = "SalonService")
    public Map<SalonService, Boolean> isFavorited(List<SalonService> services, GraphQLContext context) {
        UUID userId = context.get("userId");
        return resolveFavorited(services, SalonService::getId, userId, ItemType.SALON_SERVICE, favoriteRepository);
    }
}
