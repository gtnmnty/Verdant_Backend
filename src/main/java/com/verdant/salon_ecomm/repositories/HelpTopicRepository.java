package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.help_support.HelpTopic;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// The topic list is small and never paged, so loading articles with the topics (one query) is the right call.
public interface HelpTopicRepository extends JpaRepository<HelpTopic, UUID> {

    @EntityGraph(attributePaths = "articles")
    List<HelpTopic> findByPublishedTrueOrderBySortOrderAsc();

    @EntityGraph(attributePaths = "articles")
    List<HelpTopic> findAllByOrderBySortOrderAsc();

    //A draft slug returns empty, so unpublished topics can't be reached by guessing.
    @EntityGraph(attributePaths = "articles")
    Optional<HelpTopic> findBySlugAndPublishedTrue(String slug);

    @EntityGraph(attributePaths = "articles")
    Optional<HelpTopic> findDetailedById(UUID id);

    boolean existsBySlug(String slug);

    // -1 when there are no topics yet, so new topics get sortOrder = max + 1 = 0.
    @Query("SELECT COALESCE(MAX(t.sortOrder), -1) FROM HelpTopic t")
    int findMaxSortOrder();
}
