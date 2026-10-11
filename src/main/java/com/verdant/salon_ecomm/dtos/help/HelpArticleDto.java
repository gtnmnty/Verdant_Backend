package com.verdant.salon_ecomm.dtos.help;

import java.util.UUID;

// One answer inside a topic. On the featured card
// the title is the clickable pill and the body is the text revealed.
public record HelpArticleDto(UUID id, String title, String body) {}
