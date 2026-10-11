package com.verdant.salon_ecomm.dtos.help;

import com.verdant.salon_ecomm.models.enums.help.HelpTopicIcon;
import com.verdant.salon_ecomm.models.enums.help.HelpTopicLayout;

import java.util.List;

// New topics are added at the end of the list; use reorderHelpTopics to move them.
public record CreateHelpTopicInput(
    String title,
    String summary,
    String keywords,
    HelpTopicIcon icon,
    HelpTopicLayout layout,
    Boolean published,                 // defaults to false (draft)
    List<HelpArticleInput> articles    // display order = list order
) {}
