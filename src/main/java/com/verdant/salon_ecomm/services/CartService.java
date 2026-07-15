package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.entities.CartItem;
import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.exceptions.CartItemNotFoundException;
import com.verdant.salon_ecomm.exceptions.InvalidQuantityException;
import com.verdant.salon_ecomm.dtos.cart.CartDto;
import com.verdant.salon_ecomm.dtos.cart.CartItemDto;
import com.verdant.salon_ecomm.dtos.cart.CartInputs.AddToCartInput;
import com.verdant.salon_ecomm.dtos.cart.CartInputs.UpdateDeliveryOptionInput;
import com.verdant.salon_ecomm.dtos.cart.CartInputs.UpdateQuantityInput;
import com.verdant.salon_ecomm.mappers.CartMapper;
import com.verdant.salon_ecomm.repositories.CartItemRepository;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.specifications.CartItemSpec;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CartMapper cartMapper;

    public CartDto getCart(UUID userId) {
        List<CartItem> items = cartItemRepository.findByUser_IdOrderByAddedAtDesc(userId);
        return cartMapper.toCartDto(items);
    }

    public CartDto getSelectedCart(UUID userId, List<UUID> cartItemIds) {
        List<CartItem> items = fetchOwnedOrThrow(userId, cartItemIds);
        return cartMapper.toCartDto(items);
    }

    @Transactional
    public CartDto addItem(UUID userId, AddToCartInput input) {
        int quantity = input.getQuantity() == null ? 1 : input.getQuantity();
        if (quantity < 1) {
            throw new InvalidQuantityException("Quantity must be at least 1.");
        }

        Product product = productRepository.findById(input.getProductId())
            .orElseThrow(() -> new EntityNotFoundException("Product not found: " + input.getProductId()));

        // Merge with existing line (same product + same delivery option) instead of duplicating
        CartItem cartItem = cartItemRepository
            .findByUser_IdAndProduct_IdAndDeliveryOption(userId, input.getProductId(), input.getDeliveryOption())
            .map(existing -> {
                existing.setQuantity(existing.getQuantity() + quantity);
                return existing;
            })
            .orElseGet(() -> CartItem.builder()
                .user(User.builder().id(userId).build())
                .product(product)
                .quantity(quantity)
                .deliveryOption(input.getDeliveryOption())
                .build());

        cartItemRepository.save(cartItem);

        // Return the whole cart so the client can update badge/subtotal in one round trip
        return getCart(userId);
    }

    @Transactional
    public CartItemDto updateQuantity(UUID userId, UpdateQuantityInput input) {
        if (input.getQuantity() == null || input.getQuantity() < 1) {
            throw new InvalidQuantityException(
                "Quantity must be at least 1. Use removeCartItems to delete an item."
            );
        }

        CartItem cartItem = cartItemRepository.findByIdAndUser_Id(input.getCartItemId(), userId)
            .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + input.getCartItemId()));

        cartItem.setQuantity(input.getQuantity());
        CartItem saved = cartItemRepository.save(cartItem);
        return cartMapper.toDto(saved);
    }

    @Transactional
    public CartItemDto updateDeliveryOption(UUID userId, UpdateDeliveryOptionInput input) {
        CartItem cartItem = cartItemRepository.findByIdAndUser_Id(input.getCartItemId(), userId)
            .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + input.getCartItemId()));

        if (cartItem.getDeliveryOption() == input.getDeliveryOption()) {
            return cartMapper.toDto(cartItem);
        }

        CartItem saved = cartItemRepository
            .findByUser_IdAndProduct_IdAndDeliveryOption(userId, cartItem.getProduct().getId(), input.getDeliveryOption())
            .map(existingTarget -> {
                existingTarget.setQuantity(existingTarget.getQuantity() + cartItem.getQuantity());
                cartItemRepository.delete(cartItem);
                return cartItemRepository.save(existingTarget);
            })
            .orElseGet(() -> {
                cartItem.setDeliveryOption(input.getDeliveryOption());
                return cartItemRepository.save(cartItem);
            });

        return cartMapper.toDto(saved);
    }

    @Transactional
    public List<UUID> removeItems(UUID userId, List<UUID> cartItemIds) {
        List<CartItem> owned = fetchOwnedOrThrow(userId, cartItemIds);
        cartItemRepository.deleteAll(owned);
        return cartItemIds;
    }

    // Shared by getSelectedCart/removeItems: scopes the lookup to this user via
    // CartItemSpec and confirms every requested id actually belongs to them.
    // Compares Sets (not sizes) so duplicate ids in the request can't slip past validation,
    // and rejects a null/empty id list outright instead of matching the whole cart.
    private List<CartItem> fetchOwnedOrThrow(UUID userId, List<UUID> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new CartItemNotFoundException("No cart item ids were provided.");
        }

        List<CartItem> items = cartItemRepository.findAll(CartItemSpec.build(userId, cartItemIds));

        Set<UUID> foundIds = items.stream().map(CartItem::getId).collect(Collectors.toSet());
        Set<UUID> requestedIds = new HashSet<>(cartItemIds);

        if (!foundIds.equals(requestedIds)) {
            throw new CartItemNotFoundException("One or more selected cart items were not found in your cart.");
        }

        return items;
    }
}