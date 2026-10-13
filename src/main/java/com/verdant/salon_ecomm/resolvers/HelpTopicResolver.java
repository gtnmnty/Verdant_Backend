package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.help.CreateHelpTopicInput;
import com.verdant.salon_ecomm.dtos.help.HelpTopicDto;
import com.verdant.salon_ecomm.dtos.help.UpdateHelpTopicInput;
import com.verdant.salon_ecomm.dtos.help.admin.AdminHelpTopicDto;
import com.verdant.salon_ecomm.services.HelpTopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class HelpTopicResolver {

    private final HelpTopicService helpTopicService;

    // ── Public (no @PreAuthorize: /graphql is permitAll) ─────────

    @QueryMapping
    public List<HelpTopicDto> helpTopics() {
        return helpTopicService.getPublishedTopics();
    }

    @QueryMapping
    public HelpTopicDto helpTopic(@Argument String slug) {
        return helpTopicService.getPublishedTopic(slug);
    }

    // ── Staff ────────────────────────────────────────────────────

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public List<AdminHelpTopicDto> adminHelpTopics() {
        return helpTopicService.getAdminTopics();
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminHelpTopicDto adminHelpTopic(@Argument UUID id) {
        return helpTopicService.getAdminTopic(id);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminHelpTopicDto createHelpTopic(@Argument CreateHelpTopicInput input) {
        return helpTopicService.createTopic(input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminHelpTopicDto updateHelpTopic(@Argument UUID id, @Argument UpdateHelpTopicInput input) {
        return helpTopicService.updateTopic(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminHelpTopicDto setHelpTopicPublished(@Argument UUID id, @Argument boolean published) {
        return helpTopicService.setPublished(id, published);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public List<AdminHelpTopicDto> reorderHelpTopics(@Argument List<UUID> ids) {
        return helpTopicService.reorder(ids);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public boolean deleteHelpTopic(@Argument UUID id) {
        return helpTopicService.deleteTopic(id);
    }
}
