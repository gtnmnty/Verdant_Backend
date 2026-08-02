package com.verdant.salon_ecomm.resolvers.product;

import com.verdant.salon_ecomm.entities.Product;
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
public class ProductFavoriteResolver {

    private final FavoriteRepository favoriteRepository;

    @BatchMapping(typeName = "Product")
    public Map<Product, Boolean> isFavorited(List<Product> products, GraphQLContext context) {
        UUID userId = context.get("userId");
        return resolveFavorited(products, Product::getId, userId, ItemType.PRODUCT, favoriteRepository);
    }
}