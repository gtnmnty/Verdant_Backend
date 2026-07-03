package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.entities.Favorite;
import com.verdant.salon_ecomm.entities.Product;
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
public class ProductFavoriteResolver {

    private final FavoriteRepository favoriteRepository;

    @BatchMapping(typeName = "Product")
    public Map<Product, Boolean> isFavorited(List<Product> products, GraphQLContext context) {
        UUID userId = context.get("userId");

        if (userId == null) {
            return products.stream().collect(Collectors.toMap(p -> p, p -> false));
        }

        List<UUID> productIds = products.stream().map(Product::getId).toList();

        List<Favorite> favorites = favoriteRepository
            .findByUserIdAndTargetIdInAndTargetType(userId, productIds, ItemType.PRODUCT);

        Set<UUID> favoritedIds = favorites.stream()
            .map(Favorite::getTargetId)
            .collect(Collectors.toSet());

        return products.stream()
            .collect(Collectors.toMap(p -> p, p -> favoritedIds.contains(p.getId())));
    }
}