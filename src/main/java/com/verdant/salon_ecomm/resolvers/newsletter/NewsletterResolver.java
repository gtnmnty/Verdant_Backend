package com.verdant.salon_ecomm.resolvers.newsletter;

import com.verdant.salon_ecomm.dtos.newsletter.admin.AdminNewsletterSubscriberPage;
import com.verdant.salon_ecomm.dtos.newsletter.NewsletterSubscribeResultDto;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterSource;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterStatus;
import com.verdant.salon_ecomm.services.newsletter.NewsletterService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class NewsletterResolver {

    private final NewsletterService newsletterService;

    // ── Public (no login) ────────────────────────────────────────

    @MutationMapping
    public NewsletterSubscribeResultDto subscribeToNewsletter(
        @Argument String email, @Argument NewsletterSource source
    ) {
        return newsletterService.subscribe(email, source, clientKey());
    }

    @MutationMapping
    public boolean unsubscribeFromNewsletter(@Argument String token) {
        return newsletterService.unsubscribe(token, clientKey());
    }

    // ── Staff (subscriber emails are personal data: no receptionists) ──

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminNewsletterSubscriberPage adminNewsletterSubscribers(
        @Argument NewsletterStatus status,
        @Argument NewsletterSource source,
        @Argument String search,
        @Argument int page,
        @Argument int pageSize
    ) {
        return newsletterService.getSubscribers(status, source, search, page, pageSize);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public boolean adminRemoveNewsletterSubscriber(@Argument UUID id) {
        return newsletterService.removeSubscriber(id);
    }

    /**
     * Used only for rate limiting. Behind a proxy/load balancer getRemoteAddr() is the PROXY's address unless
     * server.forward-headers-strategy=framework is set - otherwise every visitor would share one limit.
     */
    private String clientKey() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String addr = attrs.getRequest().getRemoteAddr();
            if (addr != null) return addr;
        }
        return "unknown";
    }
}
