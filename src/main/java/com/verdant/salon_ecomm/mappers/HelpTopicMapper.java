package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.help.admin.AdminHelpTopicDto;
import com.verdant.salon_ecomm.dtos.help.HelpArticleDto;
import com.verdant.salon_ecomm.dtos.help.HelpTopicDto;
import com.verdant.salon_ecomm.models.entities.helpsupport.HelpTopic;
import com.verdant.salon_ecomm.models.entities.helpsupport.HelpArticle;
import org.springframework.stereotype.Component;

import java.util.List;

// Articles are already ordered by the entity's @OrderBy; call inside a transaction (the services do).
@Component
public class HelpTopicMapper {

    public HelpArticleDto toArticleDto(HelpArticle article) {
        return new HelpArticleDto(article.getId(), article.getTitle(), article.getBody());
    }

    private List<HelpArticleDto> articles(HelpTopic topic) {
        return topic.getArticles().stream().map(this::toArticleDto).toList();
    }

    public HelpTopicDto toPublicDto(HelpTopic topic) {
        return new HelpTopicDto(
            topic.getId(),
            topic.getSlug(),
            topic.getTitle(),
            topic.getSummary(),
            topic.getKeywords(),
            topic.getIcon(),
            topic.getLayout(),
            articles(topic)
        );
    }

    public AdminHelpTopicDto toAdminDto(HelpTopic topic) {
        return new AdminHelpTopicDto(
            topic.getId(),
            topic.getSlug(),
            topic.getTitle(),
            topic.getSummary(),
            topic.getKeywords(),
            topic.getIcon(),
            topic.getLayout(),
            topic.isPublished(),
            topic.getSortOrder(),
            articles(topic),
            topic.getCreatedAt(),
            topic.getUpdatedAt()
        );
    }
}
