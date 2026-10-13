package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.journal.CreateJournalStoryInput;
import com.verdant.salon_ecomm.dtos.journal.JournalStoryDto;
import com.verdant.salon_ecomm.dtos.journal.JournalStoryPage;
import com.verdant.salon_ecomm.dtos.journal.UpdateJournalStoryInput;
import com.verdant.salon_ecomm.dtos.journal.admin.AdminJournalStoryDto;
import com.verdant.salon_ecomm.dtos.journal.admin.AdminJournalStoryPage;
import com.verdant.salon_ecomm.models.enums.journal.JournalCategory;
import com.verdant.salon_ecomm.models.enums.journal.JournalStorySort;
import com.verdant.salon_ecomm.models.enums.journal.JournalStoryStatus;
import com.verdant.salon_ecomm.services.JournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class JournalResolver {

    private final JournalService journalService;

    // ── Public (no @PreAuthorize: /graphql is permitAll, same as products/services) ──

    @QueryMapping
    public JournalStoryPage journalStories(
        @Argument JournalCategory category,
        @Argument String search,
        @Argument int page,
        @Argument int pageSize
    ) {
        return journalService.getPublishedStories(category, search, page, pageSize);
    }

    @QueryMapping
    public JournalStoryDto journalStory(@Argument UUID id) {
        return journalService.getPublishedStory(id);
    }

    // ── Staff ────────────────────────────────────────────────────

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminJournalStoryPage adminJournalStories(
        @Argument JournalStoryStatus status,
        @Argument JournalCategory category,
        @Argument String search,
        @Argument JournalStorySort sort,
        @Argument int page,
        @Argument int pageSize
    ) {
        return journalService.getAdminStories(status, category, search, sort, page, pageSize);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminJournalStoryDto adminJournalStory(@Argument UUID id) {
        return journalService.getAdminStory(id);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminJournalStoryDto createJournalStory(
        @Argument CreateJournalStoryInput input,
        @Argument MultipartFile resultImage,
        @Argument MultipartFile productImage
    ) {
        return journalService.createStory(input, resultImage, productImage);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminJournalStoryDto updateJournalStory(
        @Argument UUID id,
        @Argument UpdateJournalStoryInput input,
        @Argument MultipartFile resultImage,
        @Argument MultipartFile productImage
    ) {
        return journalService.updateStory(id, input, resultImage, productImage);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminJournalStoryDto setJournalStoryStatus(@Argument UUID id, @Argument JournalStoryStatus status) {
        return journalService.setStatus(id, status);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public boolean deleteJournalStory(@Argument UUID id) {
        return journalService.deleteStory(id);
    }
}
