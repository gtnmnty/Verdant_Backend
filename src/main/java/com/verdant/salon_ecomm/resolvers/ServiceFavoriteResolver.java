package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.service.SalonServiceDto;
import com.verdant.salon_ecomm.entities.Favorite;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.FavoriteRepository;
import graphql.GraphQLContext;
import lombok.RequiredArgsConstructor;

import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ServiceFavoriteResolver {

    private final FavoriteRepository favoriteRepository;

    @BatchMapping
    public Map<SalonServiceDto, Boolean> isFavorited(List<SalonServiceDto> services, GraphQLContext context){
        UUID userId = context.get("userId");

        if(userId == null){
            return services.stream().collect(Collectors.toMap(
                service -> service, service -> false));
        }

        List<UUID> servicesId = services.stream().map(SalonServiceDto::id).collect(Collectors.toList());

        List<Favorite> favorites = favoriteRepository
            .findByUserIdAndTargetIdInAndTargetType(userId, servicesId, ItemType.SALON_SERVICE);

        Set<UUID> favouriteIds = favorites
            .stream().map(Favorite::getTargetId)
            .collect(Collectors.toSet());

        return services.stream().collect(Collectors
            .toMap(s -> s, s -> favouriteIds.contains(s.id())));
    }
}
