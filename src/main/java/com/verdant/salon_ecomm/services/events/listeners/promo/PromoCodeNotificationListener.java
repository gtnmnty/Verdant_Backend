package com.verdant.salon_ecomm.services.events.listeners.promo;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.dtos.promocode.events.PromoCodeAppliedToOrderEvent;
import com.verdant.salon_ecomm.dtos.promocode.events.PromoCodeCreatedEvent;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.services.NotificationService;
import com.verdant.salon_ecomm.utils.StaffNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
@RequiredArgsConstructor
public class PromoCodeNotificationListener {

    private final NotificationService notificationService;
    private final StaffNotifier staffNotifier;

    // M-38: staff fan-out through StaffNotifier (active staff only, one transaction, failures isolated).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPromoCodeCreated(PromoCodeCreatedEvent event) {
        staffNotifier.notify(ReferenceType.PROMOTION, event.promoCode().getId(), NotificationType.PROMO_CODE_ISSUED,
            "Promo code created",
            event.promoCode().getCode() + " created by " + event.actor().getFullName(),
            event.actor());
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
