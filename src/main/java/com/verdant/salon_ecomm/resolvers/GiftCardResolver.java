package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.gitftcards.*;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.services.GiftCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class GiftCardResolver {

    private final GiftCardService giftCardService;

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<GiftCardDto> myGiftCards() {
        return giftCardService.getMyGiftCards(getCurrentUserId());
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public BigDecimal myWalletBalance() {
        return giftCardService.getMyWalletBalance(getCurrentUserId());
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<GiftCardTransactionDto> myGiftCardTransactions() {
        return giftCardService.getMyTransactions(getCurrentUserId());
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public GiftCardPaymentDto purchaseGiftCard(@Argument PurchaseGiftCardInput input) {
        return giftCardService.purchaseGiftCard(getCurrentUserId(), input);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public GiftCardDto redeemGiftCard(@Argument String code) {
        return giftCardService.redeemGiftCard(getCurrentUserId(), code);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public GiftCardDto adminIssueGiftCard(@Argument AdminIssueGiftCardInput input) {
        return giftCardService.adminIssueGiftCard(getCurrentUserId(), input);
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        throw new IllegalStateException("No authenticated user found");
    }
}