package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.dtos.cart.CartDto;
import com.verdant.salon_ecomm.dtos.cart.CartItemDto;
import com.verdant.salon_ecomm.dtos.cart.CartInputs.AddToCartInput;
import com.verdant.salon_ecomm.dtos.cart.CartInputs.RemoveCartItemsInput;
import com.verdant.salon_ecomm.dtos.cart.CartInputs.SelectedCartItemsInput;
import com.verdant.salon_ecomm.dtos.cart.CartInputs.UpdateDeliveryOptionInput;
import com.verdant.salon_ecomm.dtos.cart.CartInputs.UpdateQuantityInput;
import com.verdant.salon_ecomm.services.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class CartResolver {

    private final CartService cartService;

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public CartDto myCart(@AuthenticationPrincipal User currentUser) {
        return cartService.getCart(currentUser.getId());
    }

    // For the "select which items to check out" flow: returns totals scoped to the selected items only
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public CartDto selectedCart(
        @AuthenticationPrincipal User currentUser,
        @Argument SelectedCartItemsInput input
    ) {
        return cartService.getSelectedCart(currentUser.getId(), input.getCartItemIds());
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public CartDto addToCart(
        @AuthenticationPrincipal User currentUser,
        @Argument AddToCartInput input
    ) {
        return cartService.addItem(currentUser.getId(), input);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public CartItemDto updateCartItemQuantity(
        @AuthenticationPrincipal User currentUser,
        @Argument UpdateQuantityInput input
    ) {
        return cartService.updateQuantity(currentUser.getId(), input);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public CartItemDto updateCartItemDeliveryOption(
        @AuthenticationPrincipal User currentUser,
        @Argument UpdateDeliveryOptionInput input
    ) {
        return cartService.updateDeliveryOption(currentUser.getId(), input);
    }

    // Handles both single-item and multi-item ("select how many to delete") removal
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public List<UUID> removeCartItems(
        @AuthenticationPrincipal User currentUser,
        @Argument RemoveCartItemsInput input
    ) {
        return cartService.removeItems(currentUser.getId(), input.getCartItemIds());
    }
}