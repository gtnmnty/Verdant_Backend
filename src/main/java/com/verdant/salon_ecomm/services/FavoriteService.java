package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.entities.Favorite;
import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.FavoriteRepository;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteService {

  private final FavoriteRepository favoriteRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;

  @Transactional
  public Product toggleProduct(UUID productId) {
    UUID userId = getCurrentUserId();

    Product product = productRepository.findById(productId)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    Optional<Favorite> existing = favoriteRepository
            .findByUserIdAndTargetIdAndTargetType(userId, productId, ItemType.PRODUCT);

    if (existing.isPresent()) {
      favoriteRepository.delete(existing.get());
    } else {
      Favorite favorite = Favorite.builder()
              .user(userRepository.getReferenceById(userId))
              .targetId(productId)
              .targetType(ItemType.PRODUCT)
              .build();
      favoriteRepository.save(favorite);
    }

    return product;
  }

  private UUID getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof User user) {
      return user.getId();
    }
    throw new IllegalStateException("No authenticated user found");
  }
}