package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.services.PasswordResetTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class PasswordResetResolver {

    private final PasswordResetTokenService passwordResetTokenService;

    @MutationMapping
    public boolean redeemPasswordReset(@Argument String token, @Argument String newPassword) {
        passwordResetTokenService.redeem(token, newPassword);
        return true;
    }
}
