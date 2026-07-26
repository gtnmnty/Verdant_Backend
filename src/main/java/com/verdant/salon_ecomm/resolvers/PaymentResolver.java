package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.payment.CreatePaymentInput;
import com.verdant.salon_ecomm.dtos.payment.PaymentIntentDto;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.services.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class PaymentResolver {

    private final PaymentService paymentService;

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public PaymentIntentDto createPaymentIntent(@Argument CreatePaymentInput input,
                                                @AuthenticationPrincipal User principal) {
        // ASSUMPTION: wire isAdmin the same way your other resolvers do
        // (e.g. checking principal's role against your RBAC roles). Left as
        // false here so a non-admin caller can only pay for their own order
        // until you plug in the real check.
        boolean isAdmin = false;
        return paymentService.createPaymentIntent(input, principal.getId(), isAdmin);
    }
}