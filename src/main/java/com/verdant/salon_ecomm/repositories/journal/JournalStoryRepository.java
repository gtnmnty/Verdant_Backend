package com.verdant.salon_ecomm.repositories.journal;

import com.verdant.salon_ecomm.models.entities.journal.JournalStory;
import com.verdant.salon_ecomm.models.enums.journal.JournalStoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface JournalStoryRepository
    extends JpaRepository<JournalStory, UUID>, JpaSpecificationExecutor<JournalStory> {

    // Public detail lookup: a DRAFT id returns empty, so drafts can't be reached by guessing ids. */
    Optional<JournalStory> findByIdAndStatus(UUID id, JournalStoryStatus status);
}
