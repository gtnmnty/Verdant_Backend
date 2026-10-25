package com.verdant.salon_ecomm.services.events.listeners;

import com.verdant.salon_ecomm.dtos.reviews.events.ReviewSubmittedEvent;
import com.verdant.salon_ecomm.models.entities.Review;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.utils.StaffNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
@RequiredArgsConstructor
public class ReviewNotificationListener {

    // Reviews are a staff/admin concern — the reviewer already sees their own
    // review appear immediately, so no self-notification is sent.
    // M-38: staff fan-out through StaffNotifier (active staff only, one transaction, failures isolated).
    private final StaffNotifier staffNotifier;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewSubmitted(ReviewSubmittedEvent event) {
        Review review = event.review();
        String reviewerName = review.getUser().getFullName();

        String title = event.isNewReview() ? "Review received" : "Review updated";
        String message = reviewerName + " left a " + review.getStars() + "-star review on " + event.targetName() + ".";

        staffNotifier.notify(ReferenceType.REVIEW, review.getId(), NotificationType.REVIEW_RECEIVED,
            title, message, review.getUser().getId(), reviewerName);
    }
}
