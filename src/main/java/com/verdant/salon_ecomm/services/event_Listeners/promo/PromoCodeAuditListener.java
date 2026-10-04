package com.verdant.salon_ecomm.services.event_Listeners.promo;

import com.verdant.salon_ecomm.dtos.promo_code.events.PromoCodeAppliedToOrderEvent;
import com.verdant.salon_ecomm.dtos.promo_code.events.PromoCodeCreatedEvent;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PromoCodeAuditListener {

    private final AuditLogService auditLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPromoCodeCreated(PromoCodeCreatedEvent event) {
        auditLogService.record(
            AuditEntityType.PROMOTION, event.promoCode().getId(), AuditActionType.CREATED,
            "Promo code " + event.promoCode().getCode() + " created",
            event.promoCode().getDiscountPercent() + "% off, max " + event.promoCode().getMaxUsesPerUser() + " use(s) per user",
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPromoCodeAppliedToOrder(PromoCodeAppliedToOrderEvent event) {
        auditLogService.recordSelfService(
            AuditEntityType.ORDER, event.order().getId(), AuditActionType.PAYMENT_SUCCEEDED,
            "Promo code applied to " + event.order().getOrderCode(),
            event.redemption().getPromoCode().getCode() + " — " + event.redemption().getDiscountAmount() + " discount"
        );
    }
}