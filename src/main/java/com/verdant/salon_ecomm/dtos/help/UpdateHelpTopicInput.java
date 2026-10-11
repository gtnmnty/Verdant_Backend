package com.verdant.salon_ecomm.dtos.help;

import com.verdant.salon_ecomm.models.enums.help.HelpTopicIcon;
import com.verdant.salon_ecomm.models.enums.help.HelpTopicLayout;

import java.util.List;

// Every field optional: null = unchanged.
// articles: null = unchanged; a list (even empty) REPLACES all the topic's articles.
public record UpdateHelpTopicInput(
    String title,
    String summary,
    String keywords,
    HelpTopicIcon icon,
    HelpTopicLayout layout,
    Boolean published,
    List<HelpArticleInput> articles
) {}
