package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.payment.CreatePaymentInput;
import com.verdant.salon_ecomm.dtos.payment.PaymentIntentDto;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.security.SecurityHelper;
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
    private final SecurityHelper securityHelper;

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public PaymentIntentDto createPaymentIntent(@Argument CreatePaymentInput input,
                                                @AuthenticationPrincipal User principal) {
        // Fails closed: only MANAGER, ADMIN and OWNER may create an intent for someone else's order;
        // everyone else (receptionists included) can only pay for their own.
        boolean isAdmin = securityHelper.isManagement(principal);
        return paymentService.createPaymentIntent(input, principal.getId(), isAdmin);
    }
}