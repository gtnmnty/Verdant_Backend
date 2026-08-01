package com.verdant.salon_ecomm.dtos.reviews.events;

import com.verdant.salon_ecomm.entities.Review;

public record ReviewSubmittedEvent(Review review, boolean isNewReview, String targetName) {
}
