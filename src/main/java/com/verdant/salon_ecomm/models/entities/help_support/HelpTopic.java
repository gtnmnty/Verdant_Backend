package com.verdant.salon_ecomm.models.entities.help_support;

import com.verdant.salon_ecomm.models.enums.help.HelpTopicIcon;
import com.verdant.salon_ecomm.models.enums.help.HelpTopicLayout;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
    name = "help_topics",
    indexes = @Index(name = "idx_help_topic_published_order", columnList = "published, sort_order")
)
public class HelpTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // URL-friendly unique name, generated from the title by the service (e.g. "bookings-scheduling").
    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 500)
    private String summary;

    // Space-separated words that only the search box uses.
    @Builder.Default
    @Column(nullable = false, length = 300)
    private String keywords = "";

    @Builder.Default
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private HelpTopicIcon icon = HelpTopicIcon.HELP_CIRCLE;

    @Builder.Default
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private HelpTopicLayout layout = HelpTopicLayout.STANDARD;

    @Builder.Default
    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder.Default
    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<HelpArticle> articles = new ArrayList<>();

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    //Replaces ALL articles with {@code newArticles}, in that order. Articles have no unique key,
    //so clear-and-re-add is safe here (unlike JournalStory's product links).
    public void replaceArticles(List<HelpArticle> newArticles) {
        articles.clear();
        int order = 0;
        for (HelpArticle article : newArticles) {
            article.setTopic(this);
            article.setSortOrder(order++);
            articles.add(article);
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
