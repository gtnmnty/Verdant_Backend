package com.verdant.salon_ecomm.repositories.journal;

import com.verdant.salon_ecomm.models.entities.journal.JournalStoryProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JournalStoryProductRepository extends JpaRepository<JournalStoryProduct, UUID> {

    // Products for a whole page of stories in ONE query (no per-story / per-product lazy loads).
    // Ordered by sortOrder; group by link.getStory().getId() in the service - order within each story is kept.
    @Query("""
            SELECT l FROM JournalStoryProduct l
            JOIN FETCH l.product
            WHERE l.story.id IN :storyIds
            ORDER BY l.sortOrder ASC
        """)
    List<JournalStoryProduct> findAllWithProductByStoryIds(@Param("storyIds") Collection<UUID> storyIds);
}
