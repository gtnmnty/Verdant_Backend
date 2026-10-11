package com.verdant.salon_ecomm.dtos.journal;

// width/height are required by next/image in the
// masonry grid, so they are stored with the image.
public record JournalImageDto(String url, int width, int height) {}
