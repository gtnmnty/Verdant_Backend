package com.verdant.salon_ecomm.services.event_Listeners.gitfcards;

import com.verdant.salon_ecomm.dtos.gitftcards.events.*;
import com.verdant.salon_ecomm.models.entities.GiftCard;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GiftCardAuditListener {

    private final AuditLogService auditLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardPurchased(GiftCardPurchasedEvent event) {
        GiftCard card = event.giftCard();
        auditLogService.recordSelfService(
            AuditEntityType.GIFT_CARD, card.getId(), AuditActionType.PAYMENT_INITIATED,
            "Gift card " + card.getCode() + " purchase initiated",
            "Purchased by " + event.purchaser().getFullName() + " for " + card.getInitialAmount()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardIssuedByAdmin(GiftCardIssuedByAdminEvent event) {
        GiftCard card = event.giftCard();
        auditLogService.record(
            AuditEntityType.GIFT_CARD, card.getId(), AuditActionType.ISSUED,
            "Gift card " + card.getCode() + " issued manually",
            "Issued to " + card.getRecipientEmail() + " for " + card.getInitialAmount(),
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardRedeemed(GiftCardRedeemedEvent event) {
        GiftCard card = event.giftCard();
        auditLogService.recordSelfService(
            AuditEntityType.GIFT_CARD, card.getId(), AuditActionType.REDEEMED,
            "Gift card " + card.getCode() + " redeemed",
            "Redeemed by " + event.redeemedBy().getFullName()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardAppliedToOrder(GiftCardAppliedToOrderEvent event) {
        auditLogService.recordSelfService(
            AuditEntityType.ORDER, event.order().getId(), AuditActionType.PAYMENT_SUCCEEDED,
            "Gift card balance applied to " + event.order().getOrderCode(),
            event.totalApplied() + " deducted from " + event.customer().getFullName() + "'s gift card balance"
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardForfeited(GiftCardForfeitedEvent event) {
        GiftCard card = event.giftCard();
        AuditActionType action = event.refundIssued() ? AuditActionType.PAYMENT_REFUNDED : AuditActionType.EXPIRED;
        String detail = event.refundIssued()
            ? event.forfeitedAmount() + " reissued as a new card on account deletion"
            : event.forfeitedAmount() + " forfeited on expiry";

        if (event.owner() != null) {
            auditLogService.recordSelfService(AuditEntityType.GIFT_CARD, card.getId(), action,
                "Gift card " + card.getCode() + " balance forfeited", detail);
        } else {
            auditLogService.record(AuditEntityType.GIFT_CARD, card.getId(), action,
                "Gift card " + card.getCode() + " expired unredeemed", detail, null);
        }
    }
}
