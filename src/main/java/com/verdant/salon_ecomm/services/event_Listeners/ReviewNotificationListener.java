package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.dtos.reviews.events.ReviewSubmittedEvent;
import com.verdant.salon_ecomm.entities.Review;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.AccountRole;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ReviewNotificationListener {

    // Reviews are a staff/admin concern — the reviewer already sees their own
    // review appear immediately, so no self-notification is sent.
    private static final List<AccountRole> STAFF_ROLES = List.of(
        AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.OWNER
    );

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewSubmitted(ReviewSubmittedEvent event) {
        Review review = event.review();
        String reviewerName = review.getUser().getFullName();

        String title = event.isNewReview() ? "Review received" : "Review updated";
        String message = reviewerName + " left a " + review.getStars() + "-star review on " + event.targetName() + ".";

        List<User> staff = userRepository.findByRoleIn(STAFF_ROLES);
        for (User staffMember : staff) {
            notificationService.create(new NotificationCreateDto(
                staffMember.getId(),
                NotificationType.REVIEW_RECEIVED,
                title,
                message,
                ReferenceType.REVIEW,
                review.getId(),
                NotificationPriority.INFO,
                review.getUser().getId(),
                reviewerName
            ));
        }
    }
}
