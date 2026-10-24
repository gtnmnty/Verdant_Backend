package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.giftcards.*;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.services.GiftCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.verdant.salon_ecomm.security.SecurityHelper;

@Controller
@RequiredArgsConstructor
public class GiftCardResolver {

    private final GiftCardService giftCardService;
    private final SecurityHelper securityHelper;

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<GiftCardDto> myGiftCards() {
        return giftCardService.getMyGiftCards(securityHelper.currentUserId());
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<GiftCardDto> mySentGiftCards() {
        return giftCardService.getMySentGiftCards(securityHelper.currentUserId());
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public BigDecimal myWalletBalance() {
        return giftCardService.getMyWalletBalance(securityHelper.currentUserId());
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<GiftCardTransactionDto> myGiftCardTransactions() {
        return giftCardService.getMyTransactions(securityHelper.currentUserId());
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public GiftCardPaymentDto purchaseGiftCard(@Argument PurchaseGiftCardInput input) {
        return giftCardService.purchaseGiftCard(securityHelper.currentUserId(), input);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public GiftCardDto redeemGiftCard(@Argument String code) {
        return giftCardService.redeemGiftCard(securityHelper.currentUserId(), code);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public GiftCardDto adminIssueGiftCard(@Argument AdminIssueGiftCardInput input) {
        return giftCardService.adminIssueGiftCard(securityHelper.currentUserId(), input);
    }
}
