package com.verdant.salon_ecomm.dtos.help.admin;

import com.verdant.salon_ecomm.dtos.help.HelpArticleDto;
import com.verdant.salon_ecomm.models.enums.help.HelpTopicIcon;
import com.verdant.salon_ecomm.models.enums.help.HelpTopicLayout;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminHelpTopicDto(
    UUID id,
    String slug,
    String title,
    String summary,
    String keywords,
    HelpTopicIcon icon,
    HelpTopicLayout layout,
    boolean published,
    int sortOrder,
    List<HelpArticleDto> articles,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
