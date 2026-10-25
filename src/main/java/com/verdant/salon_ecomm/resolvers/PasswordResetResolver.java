package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.services.PasswordResetTokenService;
import com.verdant.salon_ecomm.services.PublicFormRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Controller
@RequiredArgsConstructor
public class PasswordResetResolver {

    private final PasswordResetTokenService passwordResetTokenService;
    private final PublicFormRateLimiter rateLimiter;

    // Public (no login): the person following the emailed link has no password yet.
    // H-9 basic rate limit: 10 attempts per IP per 15 minutes. The token is 256-bit so guessing is not
    // realistic; the limit mainly blocks hammering and keeps this in line with the other auth endpoints.
    // Tradeoff: per-IP only, and PublicFormRateLimiter fails open if Redis is down. Behind a proxy every
    // visitor shares the proxy's address unless server.forward-headers-strategy is set (it is in the
    // docker profile). To be replaced by the planned Bucket4j / Resilience4j limiter.
    @MutationMapping
    public boolean redeemPasswordReset(@Argument String token, @Argument String newPassword) {
        rateLimiter.check("redeem-reset:ip:" + clientKey(), 10, Duration.ofMinutes(15));
        passwordResetTokenService.redeem(token, newPassword);
        return true;
    }

    private String clientKey() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String addr = attrs.getRequest().getRemoteAddr();
            if (addr != null) return addr;
        }
        return "unknown";
    }
}
