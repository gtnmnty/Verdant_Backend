package com.verdant.salon_ecomm.services.events.listeners.giftcards;

import com.verdant.salon_ecomm.dtos.giftcards.events.*;
import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.models.entities.giftcards.GiftCard;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.services.NotificationService;
import com.verdant.salon_ecomm.utils.PiiMasking;
import com.verdant.salon_ecomm.utils.StaffNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GiftCardNotificationListener {

    private final NotificationService notificationService;
    private final StaffNotifier staffNotifier;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardPurchased(GiftCardPurchasedEvent event) {
        GiftCard card = event.giftCard();

        notificationService.create(new NotificationCreateDto(
            event.purchaser().getId(),
            NotificationType.GIFT_CARD_PURCHASED,
            "Gift card purchased",
            "Payment received for your gift card " + card.maskedCode() + ".",
            ReferenceType.GIFT_CARD, card.getId(),
            NotificationPriority.INFO, null, null
        ));

        notifyStaff(card, NotificationType.GIFT_CARD_PURCHASED, "Gift card purchased",
            card.maskedCode() + " purchased by " + event.purchaser().getFullName(),
            null, null);
        // The recipient email with the code is sent by GiftCardEmailListener.
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardIssuedByAdmin(GiftCardIssuedByAdminEvent event) {
        GiftCard card = event.giftCard();

        // Recipient isn't necessarily a registered user yet (issued by email) —
        // only notify in-app if it resolves to an existing account.
        if (card.getRecipientEmail() != null) userRepository.findByEmail(card.getRecipientEmail()).ifPresent(recipient ->
            notificationService.create(new NotificationCreateDto(
                recipient.getId(),
                NotificationType.GIFT_CARD_ISSUED,
                "You received a gift card",
                "A gift card for " + card.getInitialAmount() + " was issued to you.",
                ReferenceType.GIFT_CARD, card.getId(),
                NotificationPriority.INFO, event.actor().getId(), event.actor().getFullName()
            ))
        );

        // M-22: recipient email masked. This goes to every staff member, receptionists included.
        notifyStaff(card, NotificationType.GIFT_CARD_ISSUED, "Gift card issued manually",
            card.maskedCode() + " issued by " + event.actor().getFullName()
                + " to " + (card.getRecipientEmail() != null ? PiiMasking.maskEmail(card.getRecipientEmail()) : "no recipient email"),
            event.actor().getId(), event.actor().getFullName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardRedeemed(GiftCardRedeemedEvent event) {
        GiftCard card = event.giftCard();

        notificationService.create(new NotificationCreateDto(
            event.redeemedBy().getId(),
            NotificationType.GIFT_CARD_REDEEMED,
            "Gift card redeemed",
            card.maskedCode() + " (" + card.getBalance() + ") added to your balance.",
            ReferenceType.GIFT_CARD, card.getId(),
            NotificationPriority.INFO, null, null
        ));

        notifyStaff(card, NotificationType.GIFT_CARD_REDEEMED, "Gift card redeemed",
            card.maskedCode() + " redeemed by " + event.redeemedBy().getFullName(),
            null, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardAppliedToOrder(GiftCardAppliedToOrderEvent event) {
        notificationService.create(new NotificationCreateDto(
            event.customer().getId(),
            NotificationType.GIFT_CARD_APPLIED_TO_ORDER,
            "Gift card balance applied",
            event.totalApplied() + " applied to order " + event.order().getOrderCode() + ".",
            ReferenceType.ORDER, event.order().getId(),
            NotificationPriority.INFO, null, null
        ));
        // Staff-facing: not notified per-order (would be noisy on every checkout) —
        // visible via the order's own notification/audit trail instead.
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardForfeited(GiftCardForfeitedEvent event) {
        if (event.owner() == null) return; // unredeemed card expiring — no one to notify

        NotificationType type = event.refundIssued()
            ? NotificationType.GIFT_CARD_REFUND_ISSUED : NotificationType.GIFT_CARD_EXPIRED;
        String message = event.refundIssued()
            ? event.forfeitedAmount() + " refunded as a new gift card after account deletion."
            : event.giftCard().maskedCode() + " expired with " + event.forfeitedAmount() + " remaining.";

        // On account deletion the user no longer exists - an in-app notification would point at a deleted
        // row. They are told by the refund-code email that GiftCardEmailListener sends instead.
        if (!event.refundIssued()) {
            notificationService.create(new NotificationCreateDto(
                event.owner().getId(), type, "Gift card balance forfeited", message,
                ReferenceType.GIFT_CARD, event.giftCard().getId(),
                NotificationPriority.WARNING, null, null
            ));
        }

        notifyStaff(event.giftCard(), type, "Gift card balance forfeited",
            message + " (user: " + event.owner().getFullName() + ")", null, null);
    }

    // M-38: delegates to StaffNotifier. This copy had no per-recipient try/catch, so one failed row stopped
    // every staff member after it from being notified.
    private void notifyStaff(
        GiftCard card, NotificationType type, String title, String message,
        UUID actorId, String actorName
    ) {
        staffNotifier.notify(ReferenceType.GIFT_CARD, card.getId(), type, title, message, actorId, actorName);
    }
}