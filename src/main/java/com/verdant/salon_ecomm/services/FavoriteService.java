package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.favorites.events.FavoriteToggledEvent;
import com.verdant.salon_ecomm.models.entities.Favorite;
import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.SalonService;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.FavoriteRepository;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.repositories.SalonServiceRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import com.verdant.salon_ecomm.security.SecurityHelper;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final SecurityHelper securityHelper;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Product toggleProduct(UUID productId) {
        UUID userId = securityHelper.currentUserId();

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Optional<Favorite> existing = favoriteRepository
            .findByUserIdAndTargetIdAndTargetType(userId, productId, ItemType.PRODUCT);

        boolean added;
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            added = false;
        } else {
            Favorite favorite = Favorite.builder()
                .user(userRepository.getReferenceById(userId))
                .targetId(productId)
                .targetType(ItemType.PRODUCT)
                .build();
            favoriteRepository.save(favorite);
            added = true;
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        eventPublisher.publishEvent(
            new FavoriteToggledEvent(user, ItemType.PRODUCT, productId, product.getName(), added)
        );

        return product;
    }

    @Transactional
    public SalonService toggleSalon(UUID serviceId) {
        UUID userId = securityHelper.currentUserId();

        SalonService service = salonServiceRepository.findById(serviceId)
            .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        Optional<Favorite> existing = favoriteRepository
            .findByUserIdAndTargetIdAndTargetType(userId, serviceId, ItemType.SALON_SERVICE);


        boolean added;
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            added = false;
        } else {
            Favorite favorite = Favorite.builder()
                .user(userRepository.getReferenceById(userId))
                .targetId(serviceId)
                .targetType(ItemType.SALON_SERVICE)
                .build();

            favoriteRepository.save(favorite);
            added = true;
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        eventPublisher.publishEvent(
            new FavoriteToggledEvent(user, ItemType.SALON_SERVICE, serviceId, service.getName(), added)
        );

        return service;
    }
}
