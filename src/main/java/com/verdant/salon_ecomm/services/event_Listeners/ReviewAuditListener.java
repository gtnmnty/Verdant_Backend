package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.reviews.events.ReviewSubmittedEvent;
import com.verdant.salon_ecomm.entities.Review;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReviewAuditListener {

    private final AuditLogService auditLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewSubmitted(ReviewSubmittedEvent event) {
        Review review = event.review();
        String title = (event.isNewReview() ? "Review left for " : "Review updated for ") + event.targetName();

        auditLogService.recordSelfService(
            AuditEntityType.REVIEW,
            review.getId(),
            AuditActionType.REVIEWED,
            title,
            review.getStars() + " stars — " + (review.getText() != null ? review.getText() : "no comment")
        );
    }
}
