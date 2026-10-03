package com.verdant.salon_ecomm.services.event_Listeners.gitfcards;

import com.verdant.salon_ecomm.config.StripeConfig;
import com.verdant.salon_ecomm.dtos.gitftcards.events.GiftCardForfeitedEvent;
import com.verdant.salon_ecomm.dtos.gitftcards.events.GiftCardIssuedByAdminEvent;
import com.verdant.salon_ecomm.dtos.gitftcards.events.GiftCardPurchasedEvent;
import com.verdant.salon_ecomm.models.entities.giftcards.GiftCard;
import com.verdant.salon_ecomm.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;


// Emails gift card codes. Runs after the DB commit and off the request/webhook thread (@Async), so a slow
// or failing mail server can never delay the Stripe webhook or roll back the purchase.
// Failures are logged with the card id (never the code or the address).

@Component
@RequiredArgsConstructor
@Slf4j
public class GiftCardEmailListener {

    private static final DateTimeFormatter EXPIRY_FORMAT =
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final EmailService emailService;
    private final StripeConfig stripeConfig;

    // Fires only once payment is confirmed (see GiftCardService.markPaid).
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardPurchased(GiftCardPurchasedEvent event) {
        String sender = event.purchaser() != null ? event.purchaser().getFullName() : null;
        sendToRecipient(event.giftCard(), sender);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardIssuedByAdmin(GiftCardIssuedByAdminEvent event) {
        sendToRecipient(event.giftCard(), null); // issued by the salon, not a customer
    }

    // Account-deletion refund only. Expiry forfeits have no replacement card, so nothing to send.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGiftCardForfeited(GiftCardForfeitedEvent event) {
        GiftCard replacement = event.replacementCard();
        if (!event.refundIssued() || replacement == null || replacement.getRecipientEmail() == null) return;

        try {
            emailService.sendGiftCardRefundEmail(
                replacement.getRecipientEmail(), amountText(event.forfeitedAmount()), replacement.getCode());
        } catch (Exception e) {
            // This is the only way the deleted user ever receives the code - make the failure loud.
            log.error("FAILED to email account-deletion refund gift card {} (balance {}). Needs manual follow-up.",
                replacement.getId(), event.forfeitedAmount(), e);
        }
    }

    private void sendToRecipient(GiftCard card, String senderName) {
        if (card.getRecipientEmail() == null) return; // nothing to deliver to (e.g. admin issued without an email)
        try {
            emailService.sendGiftCardEmail(
                card.getRecipientEmail(),
                card.getRecipientName(),
                senderName,
                amountText(card.getInitialAmount()),
                card.getCode(),
                card.getNote(),
                card.getExpiresAt() != null ? EXPIRY_FORMAT.format(card.getExpiresAt()) : null
            );
        } catch (Exception e) {
            log.error("Failed to email gift card {} to its recipient", card.getId(), e);
        }
    }

    private String amountText(BigDecimal amount) {
        return stripeConfig.getCurrency().toUpperCase(Locale.ROOT) + " " + amount.setScale(2, RoundingMode.HALF_UP);
    }
}
