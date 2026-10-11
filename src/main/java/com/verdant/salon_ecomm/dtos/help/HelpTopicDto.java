package com.verdant.salon_ecomm.dtos.help;

import com.verdant.salon_ecomm.models.enums.help.HelpTopicIcon;
import com.verdant.salon_ecomm.models.enums.help.HelpTopicLayout;

import java.util.List;
import java.util.UUID;

// Public view - only published topics, already in display order.
public record HelpTopicDto(
    UUID id,
    String slug,
    String title,
    String summary,       // the short blurb on the card
    String keywords,      // space-separated, used only by the search box
    HelpTopicIcon icon,
    HelpTopicLayout layout,
    List<HelpArticleDto> articles
) {}
