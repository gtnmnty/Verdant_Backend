package com.verdant.salon_ecomm.repositories.newsletter;

import com.verdant.salon_ecomm.models.entities.newsletter.NewsletterSubscriber;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NewsletterSubscriberRepository
    extends JpaRepository<NewsletterSubscriber, UUID>, JpaSpecificationExecutor<NewsletterSubscriber> {

    Optional<NewsletterSubscriber> findByEmail(String email);

    Optional<NewsletterSubscriber> findByUnsubscribeToken(String unsubscribeToken);

    long countByStatus(NewsletterStatus status);

    // Subscribers (with the given status) that have NO delivery row yet for this campaign - i.e. still to be
    // attempted. Stable order so batches are predictable.
    @Query("""
            SELECT s FROM NewsletterSubscriber s
            WHERE s.status = :status
              AND NOT EXISTS (
                  SELECT 1 FROM NewsletterDelivery d
                  WHERE d.campaign.id = :campaignId AND d.subscriber = s)
            ORDER BY s.createdAt ASC, s.id ASC
        """)
    List<NewsletterSubscriber> findPendingForCampaign(@Param("campaignId") UUID campaignId,
                                                      @Param("status") NewsletterStatus status,
                                                      Pageable pageable);
}
