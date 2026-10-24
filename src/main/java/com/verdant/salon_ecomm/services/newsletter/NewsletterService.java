package com.verdant.salon_ecomm.services.newsletter;

import com.verdant.salon_ecomm.dtos.newsletter.NewsletterSubscribeResultDto;
import com.verdant.salon_ecomm.dtos.newsletter.NewsletterSubscribedEvent;
import com.verdant.salon_ecomm.dtos.newsletter.admin.AdminNewsletterSubscriberPage;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.NewsletterMapper;
import com.verdant.salon_ecomm.models.entities.newsletter.NewsletterSubscriber;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterSource;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterStatus;
import com.verdant.salon_ecomm.repositories.newsletter.NewsletterSubscriberRepository;
import com.verdant.salon_ecomm.services.PublicFormRateLimiter;
import com.verdant.salon_ecomm.specifications.NewsletterSubscriberSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import com.verdant.salon_ecomm.utils.InputValidators;
import com.verdant.salon_ecomm.utils.PageRequests;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterService {

    private static final int MAX_EMAIL = 254;
    private static final int MAX_TOKEN = 64;
    private static final int MAX_PAGE_SIZE = 100;
    private static final SecureRandom RANDOM = new SecureRandom();

    // Public-form limits. Per-IP is generous (shared networks); per-email stops one address being hammered.
    private static final int IP_MAX_PER_HOUR = 20;
    private static final int EMAIL_MAX_PER_HOUR = 3;
    private static final Duration WINDOW = Duration.ofHours(1);

    private static final String SUBSCRIBED_MESSAGE = "You're on the list — stay inspired.";

    private final NewsletterSubscriberRepository subscriberRepository;
    private final NewsletterMapper newsletterMapper;
    private final PublicFormRateLimiter rateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    // ── Public ───────────────────────────────────────────────────

    /**
     * Not @Transactional on purpose: two simultaneous signups for the same new address make the second INSERT
     * fail on the unique constraint. That failure would poison an outer transaction, so each attempt runs in its
     * own short transaction and a duplicate is simply treated as "already subscribed".
     *
     * The response is identical for new, already-subscribed and previously-unsubscribed addresses.
     * An address that unsubscribed IS re-subscribed if someone submits it again (single opt-in, as decided).
     */
    public NewsletterSubscribeResultDto subscribe(String rawEmail, NewsletterSource source, String clientKey) {
        rateLimiter.check("newsletter:ip:" + clientKey, IP_MAX_PER_HOUR, WINDOW);

        String email = normalizeEmail(rawEmail);
        rateLimiter.check("newsletter:email:" + email, EMAIL_MAX_PER_HOUR, WINDOW);

        try {
            transactionTemplate.executeWithoutResult(tx -> {
                OffsetDateTime now = OffsetDateTime.now();
                NewsletterSubscriber existing = subscriberRepository.findByEmail(email).orElse(null);

                if (existing == null) {
                    NewsletterSubscriber created = subscriberRepository.saveAndFlush(NewsletterSubscriber.builder()
                        .email(email)
                        .status(NewsletterStatus.SUBSCRIBED)
                        .source(source)
                        .unsubscribeToken(generateToken())
                        .subscribedAt(now)
                        .build());
                    // Sent only after this transaction commits (see NewsletterEmailListener).
                    eventPublisher.publishEvent(new NewsletterSubscribedEvent(email, created.getUnsubscribeToken()));
                } else if (existing.getStatus() == NewsletterStatus.UNSUBSCRIBED) {
                    existing.setStatus(NewsletterStatus.SUBSCRIBED);
                    existing.setSubscribedAt(now);
                    existing.setUnsubscribedAt(null);
                    if (source != null) existing.setSource(source);
                    subscriberRepository.save(existing);
                    eventPublisher.publishEvent(new NewsletterSubscribedEvent(email, existing.getUnsubscribeToken()));
                }
                // already SUBSCRIBED: nothing to do
            });
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("Concurrent newsletter signup for the same address - already subscribed");
        }

        return new NewsletterSubscribeResultDto(true, SUBSCRIBED_MESSAGE);
    }

    /** True if the token belongs to a subscriber (even one already unsubscribed), false for an unknown token. */
    @Transactional
    public boolean unsubscribe(String token, String clientKey) {
        rateLimiter.check("newsletter:unsub:ip:" + clientKey, IP_MAX_PER_HOUR, WINDOW);

        if (token == null || token.isBlank() || token.length() > MAX_TOKEN) return false;

        return subscriberRepository.findByUnsubscribeToken(token.trim())
            .map(subscriber -> {
                if (subscriber.getStatus() == NewsletterStatus.SUBSCRIBED) {
                    subscriber.setStatus(NewsletterStatus.UNSUBSCRIBED);
                    subscriber.setUnsubscribedAt(OffsetDateTime.now());
                    subscriberRepository.save(subscriber);
                }
                return true;
            })
            .orElse(false);
    }

    // ── Admin ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminNewsletterSubscriberPage getSubscribers(
        NewsletterStatus status, NewsletterSource source, String search, int page, int pageSize
    ) {
        Specification<NewsletterSubscriber> spec = Specification.allOf(
            NewsletterSubscriberSpec.hasStatus(status),
            NewsletterSubscriberSpec.hasSource(source),
            NewsletterSubscriberSpec.emailContains(search)
        );
        Pageable pageable = PageRequests.of(page, pageSize, MAX_PAGE_SIZE, Sort.by(Sort.Order.desc("createdAt")));

        Page<NewsletterSubscriber> result = subscriberRepository.findAll(spec, pageable);
        return new AdminNewsletterSubscriberPage(
            result.getContent().stream().map(newsletterMapper::toAdminDto).toList(),
            pageable.getPageNumber() + 1,
            pageable.getPageSize(),
            (int) result.getTotalElements(),
            result.getTotalPages()
        );
    }

    /** Permanent erase (data-deletion requests). */
    @Transactional
    public boolean removeSubscriber(UUID id) {
        NewsletterSubscriber subscriber = subscriberRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Subscriber not found: " + id));
        subscriberRepository.delete(subscriber);
        return true;
    }

    // ── Helpers ──────────────────────────────────────────────────

    private String normalizeEmail(String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (email.isEmpty() || email.length() > MAX_EMAIL || !InputValidators.isEmail(email)) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        return email;
    }

    /** 256 random bits, URL-safe (43 chars): safe to put straight into an unsubscribe link. */
    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
