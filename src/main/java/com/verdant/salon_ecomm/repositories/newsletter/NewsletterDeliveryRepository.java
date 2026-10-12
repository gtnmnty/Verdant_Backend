package com.verdant.salon_ecomm.repositories.newsletter;

import com.verdant.salon_ecomm.dtos.newsletter.CampaignDeliveryCount;
import com.verdant.salon_ecomm.models.entities.newsletter.NewsletterDelivery;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NewsletterDeliveryRepository extends JpaRepository<NewsletterDelivery, UUID> {

    // Sent/failed totals for a whole page of campaigns in one grouped query.
    @Query("""
            SELECT new com.verdant.salon_ecomm.dtos.newsletter.CampaignDeliveryCount(d.campaign.id, d.status, COUNT(d))
            FROM NewsletterDelivery d
            WHERE d.campaign.id IN :campaignIds
            GROUP BY d.campaign.id, d.status
        """)
    List<CampaignDeliveryCount> countByCampaignIds(@Param("campaignIds") Collection<UUID> campaignIds);

    // Used before a resend: failed subscribers lose their row, so they count as "pending" again.
    @Modifying
    @Query("DELETE FROM NewsletterDelivery d WHERE d.campaign.id = :campaignId AND d.status = :status")
    int deleteByCampaignIdAndStatus(@Param("campaignId") UUID campaignId,
                                    @Param("status") NewsletterDeliveryStatus status);
}
