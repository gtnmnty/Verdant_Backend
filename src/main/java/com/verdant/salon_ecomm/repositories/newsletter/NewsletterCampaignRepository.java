package com.verdant.salon_ecomm.repositories.newsletter;

import com.verdant.salon_ecomm.models.entities.newsletter.NewsletterCampaign;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NewsletterCampaignRepository extends JpaRepository<NewsletterCampaign, UUID> {

    // createdBy is loaded with the campaigns so the mapper can show the author without a query per row.
    @Override
    @EntityGraph(attributePaths = "createdBy")
    @NonNull
    Page<NewsletterCampaign> findAll(@NonNull Pageable pageable);

    @EntityGraph(attributePaths = "createdBy")
    Optional<NewsletterCampaign> findDetailedById(UUID id);
}
