package com.verdant.salon_ecomm.services.newsletter;

import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import org.springframework.http.HttpStatus;
import com.verdant.salon_ecomm.dtos.newsletter.CampaignDeliveryCount;
import com.verdant.salon_ecomm.dtos.newsletter.CreateNewsletterCampaignInput;
import com.verdant.salon_ecomm.dtos.newsletter.UpdateNewsletterCampaignInput;
import com.verdant.salon_ecomm.dtos.newsletter.admin.AdminNewsletterCampaignDto;
import com.verdant.salon_ecomm.dtos.newsletter.admin.AdminNewsletterCampaignPage;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.NewsletterMapper;
import com.verdant.salon_ecomm.models.entities.newsletter.NewsletterCampaign;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterCampaignStatus;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterDeliveryStatus;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterStatus;
import com.verdant.salon_ecomm.repositories.newsletter.NewsletterCampaignRepository;
import com.verdant.salon_ecomm.repositories.newsletter.NewsletterDeliveryRepository;
import com.verdant.salon_ecomm.repositories.newsletter.NewsletterSubscriberRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.services.EmailService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.*;
import static com.verdant.salon_ecomm.utils.InputValidators.requireText;
import static com.verdant.salon_ecomm.utils.InputValidators.optionalText;
import com.verdant.salon_ecomm.utils.InputValidators;
import com.verdant.salon_ecomm.utils.PageRequests;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterCampaignService {

    private static final int MAX_SUBJECT = 200;
    private static final int MAX_BODY = 20_000;
    private static final int MAX_CTA_LABEL = 60;
    private static final int MAX_CTA_URL = 500;
    private static final int MAX_PAGE_SIZE = 50;

    private final NewsletterCampaignRepository campaignRepository;
    private final NewsletterDeliveryRepository deliveryRepository;
    private final NewsletterSubscriberRepository subscriberRepository;
    private final UserRepository userRepository;
    private final NewsletterMapper newsletterMapper;
    private final NewsletterCampaignSender sender;
    private final EmailService emailService;
    private final TransactionTemplate transactionTemplate;

    // Safety cap. Gmail SMTP (your current setup) blocks accounts that send to more than a few hundred
    // recipients a day. Raise this only after moving to a bulk email provider.
    @Value("${app.newsletter.max-recipients:400}")
    private int maxRecipients;

    // ── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminNewsletterCampaignPage getCampaigns(int page, int pageSize) {
        Pageable pageable = PageRequests.of(page, pageSize, MAX_PAGE_SIZE, Sort.by(Sort.Order.desc("createdAt")));
        Page<NewsletterCampaign> result = campaignRepository.findAll(pageable);

        Map<UUID, int[]> counts = loadCounts(result.getContent().stream().map(NewsletterCampaign::getId).toList());
        List<AdminNewsletterCampaignDto> items = result.getContent().stream()
            .map(c -> toDto(c, counts))
            .toList();

        return new AdminNewsletterCampaignPage(items, pageable.getPageNumber() + 1, pageable.getPageSize(),
            (int) result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminNewsletterCampaignDto getCampaign(UUID id) {
        NewsletterCampaign campaign = findOrThrow(id);
        return toDto(campaign, loadCounts(List.of(id)));
    }

    // ── Draft management ─────────────────────────────────────────

    @Transactional
    public AdminNewsletterCampaignDto createCampaign(UUID actorId, CreateNewsletterCampaignInput input) {
        String subject = requireText(input.subject(), MAX_SUBJECT, "Subject");
        String body = requireText(input.body(), MAX_BODY, "Body");
        String ctaLabel = optionalText(input.ctaLabel(), MAX_CTA_LABEL, "Button label");
        String ctaUrl = validateUrl(optionalText(input.ctaUrl(), MAX_CTA_URL, "Button link"));
        requireBothOrNeither(ctaLabel, ctaUrl);

        NewsletterCampaign campaign = campaignRepository.save(NewsletterCampaign.builder()
            .subject(subject)
            .body(body)
            .ctaLabel(ctaLabel)
            .ctaUrl(ctaUrl)
            .status(NewsletterCampaignStatus.DRAFT)
            .createdBy(userRepository.findById(actorId).orElse(null))
            .build());
        return toDto(campaign, Map.of());
    }

    @Transactional
    public AdminNewsletterCampaignDto updateCampaign(UUID id, UpdateNewsletterCampaignInput input) {
        NewsletterCampaign campaign = findOrThrow(id);
        requireDraft(campaign, "edited");

        if (input.subject() != null) campaign.setSubject(requireText(input.subject(), MAX_SUBJECT, "Subject"));
        if (input.body() != null) campaign.setBody(requireText(input.body(), MAX_BODY, "Body"));
        // "" clears the button; null leaves it alone.
        if (input.ctaLabel() != null) campaign.setCtaLabel(optionalText(input.ctaLabel(), MAX_CTA_LABEL, "Button label"));
        if (input.ctaUrl() != null) campaign.setCtaUrl(validateUrl(optionalText(input.ctaUrl(), MAX_CTA_URL, "Button link")));
        requireBothOrNeither(campaign.getCtaLabel(), campaign.getCtaUrl());

        return toDto(campaignRepository.save(campaign), Map.of());
    }

    /** Drafts only: once a campaign has been sent its delivery history is kept. */
    @Transactional
    public boolean deleteCampaign(UUID id) {
        NewsletterCampaign campaign = findOrThrow(id);
        requireDraft(campaign, "deleted");
        campaignRepository.delete(campaign);
        return true;
    }

    // ── Sending ──────────────────────────────────────────────────

    /**
     * Starts delivery in the background and returns immediately with status SENDING.
     * Sending again later (interrupted run, or a SENT campaign with failures) retries failed subscribers and
     * sends to anyone not yet attempted. Nobody who already received it gets it twice.
     * Not @Transactional: the claim, the short status transaction and the async hand-off must stay separate.
     */
    public AdminNewsletterCampaignDto sendCampaign(UUID id) {
        if (!sender.tryClaim(id)) {
            throw BusinessRuleException.conflict("This newsletter is already being sent");
        }
        try {
            AdminNewsletterCampaignDto started = transactionTemplate.execute(tx -> {
                NewsletterCampaign campaign = findOrThrow(id);

                long total = subscriberRepository.countByStatus(NewsletterStatus.SUBSCRIBED);
                if (total == 0) throw BusinessRuleException.invalid("There are no subscribers to send to");
                if (total > maxRecipients) {
                    throw BusinessRuleException.invalid("This list has " + total + " subscribers, above the safety limit of "
                        + maxRecipients + " per newsletter (app.newsletter.max-recipients). "
                        + "Move to a bulk email provider before raising it.");
                }
                if (campaign.getStatus() != NewsletterCampaignStatus.DRAFT) {
                    // Resume/retry: failed subscribers become pending again by losing their row.
                    deliveryRepository.deleteByCampaignIdAndStatus(id, NewsletterDeliveryStatus.FAILED);
                }

                campaign.setStatus(NewsletterCampaignStatus.SENDING);
                campaign.setRecipientCount((int) total);
                if (campaign.getStartedAt() == null) campaign.setStartedAt(OffsetDateTime.now());
                campaign.setCompletedAt(null);
                campaignRepository.save(campaign);
                return toDto(campaign, loadCounts(List.of(id)));
            });

            sender.run(id); // @Async: returns immediately, runs on the newsletter thread (claim is released there)
            return started;
        } catch (TaskRejectedException busy) {
            sender.release(id);
            throw BusinessRuleException.conflict("Another newsletter is queued ahead of this one. Try again in a few minutes.");
        } catch (RuntimeException e) {
            sender.release(id);
            throw e;
        }
    }

    /** Sends the campaign to ONE address so an admin can proof it first. Counts nothing and records nothing. */
    // M-26: no longer @Transactional. The SMTP send used to run inside a read-only transaction, holding a
    // pooled DB connection for as long as the mail server took. Flow: validate the address -> load the
    // campaign in a short read-only transaction -> send with no connection held. Only plain columns
    // (subject, body, CTA) are read after the transaction ends, so nothing lazy is touched.
    public boolean sendTest(UUID id, String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty() || email.length() > 254 || !InputValidators.isEmail(email)) {
            throw BusinessRuleException.invalid("Please enter a valid email address.");
        }
        NewsletterCampaign campaign = readOnlyTransaction().execute(tx -> findOrThrow(id));
        try {
            emailService.sendNewsletterEmail(
                email,
                "[TEST] " + campaign.getSubject(),
                NewsletterCampaignSender.toParagraphs(campaign.getBody()),
                campaign.getCtaLabel(),
                campaign.getCtaUrl(),
                emailService.unsubscribeUrl(null)   // test copies never carry a real subscriber's token
            );
            return true;
        } catch (MessagingException | RuntimeException e) {
            log.warn("Newsletter test send failed for campaign {}", id, e);
            // M-9: a message for the admin, so it is a BusinessRuleException (shown) rather than an
            // IllegalStateException (hidden behind "Something went wrong"). 503: the mail server failed, not the request.
            throw new BusinessRuleException("EMAIL_DELIVERY_FAILED",
                "The test email could not be sent. Check the mail settings.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    // A read-only copy of the shared template (the injected one is used for writes, so it isn't changed).
    private TransactionTemplate readOnlyTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionTemplate.getTransactionManager());
        template.setReadOnly(true);
        return template;
    }

    private NewsletterCampaign findOrThrow(UUID id) {
        return campaignRepository.findDetailedById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Newsletter not found: " + id));
    }

    private void requireDraft(NewsletterCampaign campaign, String verb) {
        if (campaign.getStatus() != NewsletterCampaignStatus.DRAFT) {
            throw BusinessRuleException.invalid("Only drafts can be " + verb + ". This newsletter has already been sent.");
        }
    }

    /** campaignId -> [sent, failed] */
    private Map<UUID, int[]> loadCounts(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, int[]> counts = new HashMap<>();
        for (CampaignDeliveryCount row : deliveryRepository.countByCampaignIds(ids)) {
            int[] pair = counts.computeIfAbsent(row.campaignId(), k -> new int[2]);
            if (row.status() == NewsletterDeliveryStatus.SENT) pair[0] = row.count().intValue();
            else pair[1] = row.count().intValue();
        }
        return counts;
    }

    private AdminNewsletterCampaignDto toDto(NewsletterCampaign campaign, Map<UUID, int[]> counts) {
        int[] pair = counts.getOrDefault(campaign.getId(), new int[2]);
        return newsletterMapper.toCampaignDto(campaign, pair[0], pair[1]);
    }

    /** Only https links: this URL goes into an email button, so no javascript:, data:, or plain http. */
    private String validateUrl(String url) {
        if (url == null) return null;
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw BusinessRuleException.invalid("Button link must be a full https:// address");
            }
        } catch (IllegalArgumentException e) {
            throw BusinessRuleException.invalid("Button link must be a full https:// address");
        }
        return url;
    }

    private void requireBothOrNeither(String label, String url) {
        if ((label == null) != (url == null)) {
            throw BusinessRuleException.invalid("Button needs both a label and a link (or neither)");
        }
    }
}
