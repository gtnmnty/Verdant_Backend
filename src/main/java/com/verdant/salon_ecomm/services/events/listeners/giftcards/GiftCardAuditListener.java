package com.verdant.salon_ecomm.services.events.listeners.giftcards;

import com.verdant.salon_ecomm.dtos.giftcards.events.*;
import com.verdant.salon_ecomm.models.entities.giftcards.GiftCard;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import com.verdant.salon_ecomm.utils.PiiMasking;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GiftCardAuditListener {

    private final AuditLogService auditLogService;

    // Fired from the Stripe webhook once the purchase is paid, so it's a System entry.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardPurchased(GiftCardPurchasedEvent event) {
        GiftCard card = event.giftCard();
        auditLogService.record(
            AuditEntityType.GIFT_CARD, card.getId(), AuditActionType.PAYMENT_SUCCEEDED,
            "Gift card " + card.maskedCode() + " purchase paid",
            "Purchased by " + event.purchaser().getFullName() + " for " + card.getInitialAmount(),
            null
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardIssuedByAdmin(GiftCardIssuedByAdminEvent event) {
        GiftCard card = event.giftCard();
        auditLogService.record(
            AuditEntityType.GIFT_CARD, card.getId(), AuditActionType.ISSUED,
            "Gift card " + card.maskedCode() + " issued manually",
            // M-22: masked; the full address stays on the card record.
            "Issued to " + (card.getRecipientEmail() != null ? PiiMasking.maskEmail(card.getRecipientEmail()) : "no recipient email")
                + " for " + card.getInitialAmount(),
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardRedeemed(GiftCardRedeemedEvent event) {
        GiftCard card = event.giftCard();
        auditLogService.recordSelfService(
            AuditEntityType.GIFT_CARD, card.getId(), AuditActionType.REDEEMED,
            "Gift card " + card.maskedCode() + " redeemed",
            "Redeemed by " + event.redeemedBy().getFullName(),
            event.redeemedBy()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardAppliedToOrder(GiftCardAppliedToOrderEvent event) {
        auditLogService.recordSelfService(
            AuditEntityType.ORDER, event.order().getId(), AuditActionType.GIFT_CARD_APPLIED,
            "Gift card balance applied to " + event.order().getOrderCode(),
            event.totalApplied() + " deducted from " + event.customer().getFullName() + "'s gift card balance",
            event.customer()
        );
    }

    // M-12: the "flag for admin review". A SECURITY entry on the card, filterable in the admin audit log:
    // an order using this card's balance was cancelled, but the card is voided, so nothing went back.
    // An admin decides whether the customer is owed anything. Fired from webhooks or staff actions: System.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardRestoreBlocked(GiftCardRestoreBlockedEvent event) {
        GiftCard card = event.giftCard();
        auditLogService.record(
            AuditEntityType.GIFT_CARD, card.getId(), AuditActionType.SECURITY,
            "Wallet refund blocked for voided gift card " + card.maskedCode(),
            event.amount() + " from cancelled order " + event.order().getOrderCode()
                + " was not restored (card " + card.getStatus() + ", payment " + card.getPaymentStatus()
                + "). Review needed.",
            null
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardForfeited(GiftCardForfeitedEvent event) {
        GiftCard card = event.giftCard();
        AuditActionType action = event.refundIssued() ? AuditActionType.PAYMENT_REFUNDED : AuditActionType.EXPIRED;
        String detail = event.refundIssued()
            ? event.forfeitedAmount() + " reissued as a new card on account deletion"
            : event.forfeitedAmount() + " forfeited on expiry";

        // Forfeiture on account deletion follows the user's own action; expiry comes from the hourly sweep (System).
        if (event.refundIssued() && event.owner() != null) {
            auditLogService.recordSelfService(AuditEntityType.GIFT_CARD, card.getId(), action,
                "Gift card " + card.maskedCode() + " balance forfeited", detail, event.owner());
        } else {
            String title = event.owner() != null
                ? "Gift card " + card.maskedCode() + " balance forfeited"
                : "Gift card " + card.maskedCode() + " expired unredeemed";
            auditLogService.record(AuditEntityType.GIFT_CARD, card.getId(), action, title, detail, null);
        }
    }
}