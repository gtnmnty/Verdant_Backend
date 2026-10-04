package com.verdant.salon_ecomm.services.event_Listeners.promo;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.dtos.promo_code.events.PromoCodeAppliedToOrderEvent;
import com.verdant.salon_ecomm.dtos.promo_code.events.PromoCodeCreatedEvent;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
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
public class PromoCodeNotificationListener {

    private static final List<AccountRole> STAFF_ROLES = List.of(
        AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.OWNER
    );

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPromoCodeCreated(PromoCodeCreatedEvent event) {
        for (User staffMember : userRepository.findByRoleIn(STAFF_ROLES)) {
            notificationService.create(new NotificationCreateDto(
                staffMember.getId(),
                NotificationType.PROMO_CODE_ISSUED,
                "Promo code created",
                event.promoCode().getCode() + " created by " + event.actor().getFullName(),
                ReferenceType.PROMOTION, event.promoCode().getId(),
                NotificationPriority.INFO, event.actor().getId(), event.actor().getFullName()
            ));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPromoCodeAppliedToOrder(PromoCodeAppliedToOrderEvent event) {
        notificationService.create(new NotificationCreateDto(
            event.customer().getId(),
            NotificationType.PROMO_CODE_ISSUED, // reuse — "applied" isn't a distinct customer-facing concern
            "Promo code applied",
            event.redemption().getPromoCode().getCode() + " saved you "
                + event.redemption().getDiscountAmount() + " on " + event.order().getOrderCode() + ".",
            ReferenceType.ORDER, event.order().getId(),
            NotificationPriority.INFO, null, null
        ));
        // No staff notification per redemption — same reasoning as gift-card-applied-to-order: noisy on every checkout.
    }
}
