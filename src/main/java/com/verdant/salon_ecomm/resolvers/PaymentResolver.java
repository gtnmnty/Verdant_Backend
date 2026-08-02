package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.payment.CreatePaymentInput;
import com.verdant.salon_ecomm.dtos.payment.PaymentIntentDto;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
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
        // ASSUMPTION: User.getRole() returns your AccountRole enum, matching
        // the pattern used elsewhere (e.g. NotificationResolver's
        // hasElevatedRole check). Fails closed: any role other than ADMIN
        // results in isAdmin=false, so a non-admin or non-manager can only pay for
        // their own order.
        boolean isAdmin = principal.getRole() == AccountRole.ADMIN ||
                          principal.getRole() == AccountRole.MANAGER;
        return paymentService.createPaymentIntent(input, principal.getId(), isAdmin);
    }
}