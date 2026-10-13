package com.verdant.salon_ecomm.services.newsletter;

import com.verdant.salon_ecomm.models.entities.newsletter.NewsletterCampaign;
import com.verdant.salon_ecomm.models.entities.newsletter.NewsletterDelivery;
import com.verdant.salon_ecomm.models.entities.newsletter.NewsletterSubscriber;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterCampaignStatus;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterDeliveryStatus;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterStatus;
import com.verdant.salon_ecomm.repositories.newsletter.NewsletterCampaignRepository;
import com.verdant.salon_ecomm.repositories.newsletter.NewsletterDeliveryRepository;
import com.verdant.salon_ecomm.repositories.newsletter.NewsletterSubscriberRepository;
import com.verdant.salon_ecomm.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delivers one campaign on the dedicated single-thread executor. Deliberately NOT transactional: every email is
 * followed by its own tiny commit (the delivery row), so progress is saved continuously and a crash loses at most
 * the one email in flight.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NewsletterCampaignSender {

    private static final int BATCH_SIZE = 50;
    // If this many sends fail in a row the problem is almost certainly the mail account (quota, auth, outage),
    // not the recipients - stop instead of marking the whole list as failed.
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    private final NewsletterCampaignRepository campaignRepository;
    private final NewsletterSubscriberRepository subscriberRepository;
    private final NewsletterDeliveryRepository deliveryRepository;
    private final EmailService emailService;

    // Pause between emails so SMTP providers don't throttle or flag the account.
    @Value("${app.newsletter.send-delay-ms:300}")
    private long sendDelayMs;

    // In-memory: protects against double-clicks and overlapping runs on THIS instance only.
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    // Returns false if the campaign is already being sent. Always pair with release() on failure paths.
    public boolean tryClaim(UUID campaignId) {
        return running.add(campaignId);
    }

    public void release(UUID campaignId) {
        running.remove(campaignId);
    }

    @Async("newsletterExecutor")
    public void run(UUID campaignId) {
        try {
            NewsletterCampaign campaign = campaignRepository.findById(campaignId).orElse(null);
            if (campaign == null) return;

            List<String> paragraphs = toParagraphs(campaign.getBody());
            int consecutiveFailures = 0;

            while (true) {
                // Only subscribers with NO delivery row are returned, so failed ones aren't picked up again in this run.
                // Re-queried every batch: someone who unsubscribes mid-send is skipped.
                List<NewsletterSubscriber> batch = subscriberRepository.findPendingForCampaign(
                    campaignId, NewsletterStatus.SUBSCRIBED, PageRequest.of(0, BATCH_SIZE));
                if (batch.isEmpty()) break;

                for (NewsletterSubscriber subscriber : batch) {
                    boolean ok = sendOne(campaign, paragraphs, subscriber);
                    consecutiveFailures = ok ? 0 : consecutiveFailures + 1;

                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        log.error("Newsletter {} stopped after {} consecutive failures - check the mail account/quota. "
                            + "It stays in SENDING; send it again to resume.", campaignId, consecutiveFailures);
                        return;
                    }
                    if (!pause()) return; // interrupted (shutdown): leave SENDING so it can be resumed
                }
            }

            campaign.setStatus(NewsletterCampaignStatus.SENT);
            campaign.setCompletedAt(OffsetDateTime.now());
            campaignRepository.save(campaign);
            log.info("Newsletter {} finished", campaignId);
        } catch (Exception e) {
            // Leaves the campaign in SENDING; the admin can send again to resume where it stopped.
            log.error("Newsletter {} aborted unexpectedly", campaignId, e);
        } finally {
            running.remove(campaignId);
        }
    }

    private boolean sendOne(NewsletterCampaign campaign, List<String> paragraphs, NewsletterSubscriber subscriber) {
        NewsletterDeliveryStatus status;
        String error = null;
        try {
            emailService.sendNewsletterEmail(
                subscriber.getEmail(),
                campaign.getSubject(),
                paragraphs,
                campaign.getCtaLabel(),
                campaign.getCtaUrl(),
                emailService.unsubscribeUrl(subscriber.getUnsubscribeToken())
            );
            status = NewsletterDeliveryStatus.SENT;
        } catch (Exception e) {
            status = NewsletterDeliveryStatus.FAILED;
            error = truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 500);
            log.warn("Newsletter {}: delivery to subscriber {} failed: {}", campaign.getId(), subscriber.getId(), error);
        }

        deliveryRepository.save(NewsletterDelivery.builder()
            .campaign(campaign)
            .subscriber(subscriber)
            .status(status)
            .error(error)
            .build());
        return status == NewsletterDeliveryStatus.SENT;
    }

    private boolean pause() {
        if (sendDelayMs <= 0) return true;
        try {
            Thread.sleep(sendDelayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Blank lines separate paragraphs; single line breaks inside a paragraph are kept as spaces. */
    static List<String> toParagraphs(String body) {
        return Arrays.stream(body.replace("\r\n", "\n").split("\n\\s*\n"))
            .map(p -> p.trim().replaceAll("\\s*\n\\s*", " "))
            .filter(p -> !p.isEmpty())
            .toList();
    }

    private String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
