package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.services.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class FavoriteResolver {

    private final FavoriteService favoriteService;

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Product toggleFavoriteProduct(@Argument String targetId) {
        return favoriteService.toggleProduct(UUID.fromString(targetId));
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public SalonService toggleFavoriteService(@Argument String targetId) {
        return favoriteService.toggleSalon(UUID.fromString(targetId));
    }
}