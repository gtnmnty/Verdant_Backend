package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.help.CreateHelpTopicInput;
import com.verdant.salon_ecomm.dtos.help.HelpArticleInput;
import com.verdant.salon_ecomm.dtos.help.HelpTopicDto;
import com.verdant.salon_ecomm.dtos.help.UpdateHelpTopicInput;
import com.verdant.salon_ecomm.dtos.help.admin.AdminHelpTopicDto;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.HelpTopicMapper;
import com.verdant.salon_ecomm.models.entities.helpsupport.HelpArticle;
import com.verdant.salon_ecomm.models.entities.helpsupport.HelpTopic;
import com.verdant.salon_ecomm.models.enums.help.HelpTopicIcon;
import com.verdant.salon_ecomm.models.enums.help.HelpTopicLayout;
import com.verdant.salon_ecomm.repositories.HelpTopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import static com.verdant.salon_ecomm.utils.InputValidators.requireText;
import static com.verdant.salon_ecomm.utils.InputValidators.optionalText;

@Service
@RequiredArgsConstructor
public class HelpTopicService {

    private static final int MAX_TITLE = 150;
    private static final int MAX_SUMMARY = 500;
    private static final int MAX_KEYWORDS = 300;
    private static final int MAX_ARTICLE_BODY = 5000;
    private static final int MAX_ARTICLES = 20;
    private static final int MAX_SLUG = 100;

    private final HelpTopicRepository helpTopicRepository;
    private final HelpTopicMapper helpTopicMapper;

    // ── Public ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<HelpTopicDto> getPublishedTopics() {
        return helpTopicRepository.findByPublishedTrueOrderBySortOrderAsc().stream()
            .map(helpTopicMapper::toPublicDto)
            .toList();
    }

    // Null for unknown slugs AND for drafts - a draft must look exactly like a missing topic.
    @Transactional(readOnly = true)
    public HelpTopicDto getPublishedTopic(String slug) {
        if (slug == null || slug.isBlank()) return null;
        return helpTopicRepository.findBySlugAndPublishedTrue(slug.trim().toLowerCase())
            .map(helpTopicMapper::toPublicDto)
            .orElse(null);
    }

    // ── Admin reads ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminHelpTopicDto> getAdminTopics() {
        return helpTopicRepository.findAllByOrderBySortOrderAsc().stream()
            .map(helpTopicMapper::toAdminDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public AdminHelpTopicDto getAdminTopic(UUID id) {
        return helpTopicMapper.toAdminDto(findTopicOrThrow(id));
    }

    // ── Admin writes ─────────────────────────────────────────────

    @Transactional
    public AdminHelpTopicDto createTopic(CreateHelpTopicInput input) {
        String title = requireText(input.title(), MAX_TITLE, "Title");
        String summary = requireText(input.summary(), MAX_SUMMARY, "Summary");
        String keywords = optionalText(input.keywords(), MAX_KEYWORDS, "Keywords");
        List<HelpArticle> articles = buildArticles(input.articles());

        HelpTopic topic = HelpTopic.builder()
            .slug(uniqueSlug(title))
            .title(title)
            .summary(summary)
            .keywords(keywords != null ? keywords : "")
            .icon(input.icon() != null ? input.icon() : HelpTopicIcon.HELP_CIRCLE)
            .layout(input.layout() != null ? input.layout() : HelpTopicLayout.STANDARD)
            .published(Boolean.TRUE.equals(input.published()))
            .sortOrder(helpTopicRepository.findMaxSortOrder() + 1)   // always added at the end
            .build();
        topic.replaceArticles(articles);

        return helpTopicMapper.toAdminDto(helpTopicRepository.save(topic));
    }

    // The slug is NOT regenerated when the title changes, so existing links to the topic keep working.
    @Transactional
    public AdminHelpTopicDto updateTopic(UUID id, UpdateHelpTopicInput input) {
        HelpTopic topic = findTopicOrThrow(id);

        String title = input.title() != null ? requireText(input.title(), MAX_TITLE, "Title") : null;
        String summary = input.summary() != null ? requireText(input.summary(), MAX_SUMMARY, "Summary") : null;
        String keywords = optionalText(input.keywords(), MAX_KEYWORDS, "Keywords");
        List<HelpArticle> articles = input.articles() == null ? null : buildArticles(input.articles());

        if (title != null) topic.setTitle(title);
        if (summary != null) topic.setSummary(summary);
        if (input.keywords() != null) topic.setKeywords(keywords != null ? keywords : "");
        if (input.icon() != null) topic.setIcon(input.icon());
        if (input.layout() != null) topic.setLayout(input.layout());
        if (input.published() != null) topic.setPublished(input.published());
        if (articles != null) topic.replaceArticles(articles);

        return helpTopicMapper.toAdminDto(helpTopicRepository.save(topic));
    }

    @Transactional
    public AdminHelpTopicDto setPublished(UUID id, boolean published) {
        HelpTopic topic = findTopicOrThrow(id);
        topic.setPublished(published);
        return helpTopicMapper.toAdminDto(helpTopicRepository.save(topic));
    }

    // ids must be every existing topic exactly once; the position in the list becomes the display order.
    @Transactional
    public List<AdminHelpTopicDto> reorder(List<UUID> ids) {
        if (ids == null) throw new IllegalArgumentException("A list of topic ids is required");

        List<HelpTopic> topics = helpTopicRepository.findAllByOrderBySortOrderAsc();
        Set<UUID> existing = topics.stream().map(HelpTopic::getId).collect(Collectors.toSet());
        if (ids.size() != existing.size() || !existing.equals(new HashSet<>(ids))) {
            throw new IllegalArgumentException("The list must contain every topic exactly once");
        }

        Map<UUID, HelpTopic> byId = topics.stream().collect(Collectors.toMap(HelpTopic::getId, t -> t));
        int order = 0;
        for (UUID id : ids) byId.get(id).setSortOrder(order++);
        helpTopicRepository.saveAll(topics);

        return topics.stream()
            .sorted(Comparator.comparingInt(HelpTopic::getSortOrder))
            .map(helpTopicMapper::toAdminDto)
            .toList();
    }

    // Articles go with it (cascade). Gaps left in sortOrder are harmless - they only need to sort correctly.
    @Transactional
    public boolean deleteTopic(UUID id) {
        helpTopicRepository.delete(findTopicOrThrow(id));
        return true;
    }

    // ── Helpers ──────────────────────────────────────────────────

    private HelpTopic findTopicOrThrow(UUID id) {
        return helpTopicRepository.findDetailedById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Help topic not found: " + id));
    }

    private List<HelpArticle> buildArticles(List<HelpArticleInput> inputs) {
        if (inputs == null) return List.of();
        if (inputs.size() > MAX_ARTICLES) {
            throw new IllegalArgumentException("A topic can have at most " + MAX_ARTICLES + " articles");
        }
        List<HelpArticle> articles = new ArrayList<>();
        for (HelpArticleInput in : inputs) {
            articles.add(HelpArticle.builder()
                .title(requireText(in.title(), MAX_TITLE, "Article title"))
                .body(requireText(in.body(), MAX_ARTICLE_BODY, "Article body"))
                .build());
        }
        return articles;
    }

    // "Bookings & Scheduling" -> "bookings-scheduling"; adds -2, -3... if the slug is taken.
    private String uniqueSlug(String title) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        if (base.length() > MAX_SLUG) base = base.substring(0, MAX_SLUG).replaceAll("-+$", "");
        if (base.isEmpty()) base = "topic";

        String candidate = base;
        for (int n = 2; helpTopicRepository.existsBySlug(candidate); n++) {
            if (n > 100) throw new IllegalStateException("Could not generate a unique slug");
            candidate = base + "-" + n;
        }
        return candidate;
    }
}
