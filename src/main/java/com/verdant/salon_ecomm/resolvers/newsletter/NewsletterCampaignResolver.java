package com.verdant.salon_ecomm.resolvers.newsletter;

import com.verdant.salon_ecomm.dtos.newsletter.admin.AdminNewsletterCampaignDto;
import com.verdant.salon_ecomm.dtos.newsletter.admin.AdminNewsletterCampaignPage;
import com.verdant.salon_ecomm.dtos.newsletter.CreateNewsletterCampaignInput;
import com.verdant.salon_ecomm.dtos.newsletter.UpdateNewsletterCampaignInput;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.services.newsletter.NewsletterCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.UUID;
import com.verdant.salon_ecomm.security.SecurityHelper;

@Controller
@RequiredArgsConstructor
public class NewsletterCampaignResolver {

    private final NewsletterCampaignService campaignService;
    private final SecurityHelper securityHelper;

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminNewsletterCampaignPage adminNewsletterCampaigns(@Argument int page, @Argument int pageSize) {
        return campaignService.getCampaigns(page, pageSize);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminNewsletterCampaignDto adminNewsletterCampaign(@Argument UUID id) {
        return campaignService.getCampaign(id);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminNewsletterCampaignDto createNewsletterCampaign(@Argument CreateNewsletterCampaignInput input) {
        return campaignService.createCampaign(securityHelper.currentUserId(), input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminNewsletterCampaignDto updateNewsletterCampaign(
        @Argument UUID id, @Argument UpdateNewsletterCampaignInput input
    ) {
        return campaignService.updateCampaign(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public boolean deleteNewsletterCampaign(@Argument UUID id) {
        return campaignService.deleteCampaign(id);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminNewsletterCampaignDto sendNewsletterCampaign(@Argument UUID id) {
        return campaignService.sendCampaign(id);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public boolean sendNewsletterTest(@Argument UUID id, @Argument String email) {
        return campaignService.sendTest(id, email);
    }
}
