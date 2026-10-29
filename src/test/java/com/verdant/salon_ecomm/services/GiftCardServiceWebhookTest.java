package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.giftcards.events.GiftCardPurchasedEvent;
import com.verdant.salon_ecomm.dtos.giftcards.events.GiftCardStripeWebhookEvent;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.entities.giftcards.GiftCard;
import com.verdant.salon_ecomm.models.entities.giftcards.GiftCardTransaction;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.giftcards.GiftCardStatus;
import com.verdant.salon_ecomm.models.enums.giftcards.GiftCardTransactionType;
import com.verdant.salon_ecomm.repositories.GiftCardRepository;
import com.verdant.salon_ecomm.repositories.GiftCardTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// The Stripe webhook side of GiftCardService: payment success, failures, full refunds/chargebacks and the
// M-14 partial-refund ledger (delta against what was already deducted, idempotent on redelivery).
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GiftCardServiceWebhookTest {

    private static final String PI = "pi_test_123";

    @Mock GiftCardRepository giftCardRepository;
    @Mock GiftCardTransactionRepository transactionRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    // Remaining constructor dependencies are not used by the webhook path.
    @Mock com.verdant.salon_ecomm.repositories.UserRepository userRepository;
    @Mock com.verdant.salon_ecomm.mappers.GiftCardMapper giftCardMapper;
    @Mock PaymentService paymentService;
    @Mock GiftCardStripeGateway stripeGateway;
    @Mock GiftCardRedeemLimiter redeemLimiter;
    @Mock org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    @InjectMocks GiftCardService service;

    private GiftCard card(PaymentStatus payment, GiftCardStatus status, String balance) {
        GiftCard card = GiftCard.builder().id(UUID.randomUUID()).code("VLX-ABCD-EF12")
            .initialAmount(new BigDecimal("100.00")).balance(new BigDecimal(balance))
            .refundedAmount(BigDecimal.ZERO).paymentStatus(payment).status(status)
            .stripePaymentIntentId(PI).purchaser(User.builder().id(UUID.randomUUID()).build()).build();
        when(giftCardRepository.findByStripePaymentIntentId(PI)).thenReturn(Optional.of(card));
        return card;
    }

    private void webhook(String type) {
        service.onStripeWebhook(new GiftCardStripeWebhookEvent(PI, type, null, null));
    }

    private void refund(boolean full, String refundedSoFar) {
        service.onStripeWebhook(new GiftCardStripeWebhookEvent(PI, "charge.refunded", full,
            refundedSoFar == null ? null : new BigDecimal(refundedSoFar)));
    }

    private GiftCardTransaction onlyLedgerRow() {
        ArgumentCaptor<GiftCardTransaction> row = ArgumentCaptor.forClass(GiftCardTransaction.class);
        verify(transactionRepository).save(row.capture());
        return row.getValue();
    }

    // ── payment success ──────────────────────────────────

    @Test
    void successMarksPaidWritesPurchaseRowAndPublishesOnce() {
        GiftCard card = card(PaymentStatus.PENDING, GiftCardStatus.ACTIVE, "100.00");

        webhook("payment_intent.succeeded");
        webhook("payment_intent.succeeded");   // duplicate delivery

        assertThat(card.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        GiftCardTransaction row = onlyLedgerRow();
        assertThat(row.getType()).isEqualTo(GiftCardTransactionType.PURCHASE);
        assertThat(row.getAmount()).isEqualByComparingTo("100.00");
        verify(eventPublisher, times(1)).publishEvent(any(GiftCardPurchasedEvent.class));
    }

    @Test
    void lateSuccessNeverRevivesARefundedOrPartiallyRefundedCard() {
        GiftCard refunded = card(PaymentStatus.REFUNDED, GiftCardStatus.CANCELLED, "0.00");
        webhook("payment_intent.succeeded");
        assertThat(refunded.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refunded.getStatus()).isEqualTo(GiftCardStatus.CANCELLED);

        GiftCard partial = card(PaymentStatus.PARTIALLY_REFUNDED, GiftCardStatus.ACTIVE, "60.00");
        webhook("payment_intent.succeeded");
        assertThat(partial.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);

        verifyNoInteractions(transactionRepository, eventPublisher);
    }

    @Test
    void successAfterAbandonedCancellationReinstatesTheCard() {
        GiftCard card = card(PaymentStatus.CANCELLED, GiftCardStatus.CANCELLED, "100.00");

        webhook("payment_intent.succeeded");

        assertThat(card.getStatus()).isEqualTo(GiftCardStatus.ACTIVE);
        assertThat(card.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    // ── failures ─────────────────────────────────────────

    @Test
    void failureIsRecordedButNeverOverwritesPaid() {
        GiftCard pending = card(PaymentStatus.PENDING, GiftCardStatus.ACTIVE, "100.00");
        webhook("payment_intent.payment_failed");
        assertThat(pending.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(pending.getStatus()).isEqualTo(GiftCardStatus.ACTIVE);   // customer may retry

        GiftCard paid = card(PaymentStatus.PAID, GiftCardStatus.ACTIVE, "100.00");
        webhook("payment_intent.payment_failed");
        webhook("payment_intent.canceled");
        assertThat(paid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.getStatus()).isEqualTo(GiftCardStatus.ACTIVE);
    }

    @Test
    void canceledIntentCancelsAnUnpaidCard() {
        GiftCard card = card(PaymentStatus.PENDING, GiftCardStatus.ACTIVE, "100.00");

        webhook("payment_intent.canceled");

        assertThat(card.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(card.getStatus()).isEqualTo(GiftCardStatus.CANCELLED);
    }

    // ── full refund / chargeback ─────────────────────────

    @Test
    void fullRefundVoidsTheCardAndLedgersTheRemainingBalance() {
        GiftCard card = card(PaymentStatus.PAID, GiftCardStatus.ACTIVE, "70.00");

        refund(true, "100.00");

        assertThat(card.getBalance()).isEqualByComparingTo("0");
        assertThat(card.getStatus()).isEqualTo(GiftCardStatus.CANCELLED);
        assertThat(card.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        GiftCardTransaction row = onlyLedgerRow();
        assertThat(row.getType()).isEqualTo(GiftCardTransactionType.REFUND);
        assertThat(row.getAmount()).isEqualByComparingTo("-70.00");
    }

    @Test
    void disputeVoidsTheCardImmediately() {
        GiftCard card = card(PaymentStatus.PAID, GiftCardStatus.ACTIVE, "100.00");

        webhook("charge.dispute.created");

        assertThat(card.getStatus()).isEqualTo(GiftCardStatus.CANCELLED);
        assertThat(card.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void fullRefundAfterAPartialOneStillVoids() {
        GiftCard card = card(PaymentStatus.PARTIALLY_REFUNDED, GiftCardStatus.ACTIVE, "60.00");
        card.setRefundedAmount(new BigDecimal("40.00"));

        refund(true, "100.00");

        assertThat(card.getStatus()).isEqualTo(GiftCardStatus.CANCELLED);
        assertThat(card.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refundOfAnUnpaidCardChangesNothing() {
        GiftCard card = card(PaymentStatus.PENDING, GiftCardStatus.ACTIVE, "100.00");

        refund(true, "100.00");
        refund(false, "30.00");

        assertThat(card.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(card.getBalance()).isEqualByComparingTo("100.00");
        verifyNoInteractions(transactionRepository);
    }

    // ── M-14 partial refunds ─────────────────────────────

    @Test
    void partialRefundTakesTheRefundedPartOffTheBalance() {
        GiftCard card = card(PaymentStatus.PAID, GiftCardStatus.ACTIVE, "100.00");

        refund(false, "30.00");

        assertThat(card.getBalance()).isEqualByComparingTo("70.00");
        assertThat(card.getRefundedAmount()).isEqualByComparingTo("30.00");
        assertThat(card.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(card.getStatus()).isEqualTo(GiftCardStatus.ACTIVE);   // still usable for what is left
        GiftCardTransaction row = onlyLedgerRow();
        assertThat(row.getType()).isEqualTo(GiftCardTransactionType.REFUND);
        assertThat(row.getAmount()).isEqualByComparingTo("-30.00");
    }

    @Test
    void secondPartialRefundDeductsOnlyTheNewDelta() {
        GiftCard card = card(PaymentStatus.PARTIALLY_REFUNDED, GiftCardStatus.ACTIVE, "70.00");
        card.setRefundedAmount(new BigDecimal("30.00"));

        refund(false, "50.00");   // Stripe reports the cumulative total

        assertThat(card.getBalance()).isEqualByComparingTo("50.00");
        assertThat(card.getRefundedAmount()).isEqualByComparingTo("50.00");
        assertThat(onlyLedgerRow().getAmount()).isEqualByComparingTo("-20.00");
    }

    @Test
    void redeliveredOrOutOfOrderPartialRefundChangesNothing() {
        GiftCard card = card(PaymentStatus.PARTIALLY_REFUNDED, GiftCardStatus.ACTIVE, "50.00");
        card.setRefundedAmount(new BigDecimal("50.00"));

        refund(false, "50.00");   // same event again
        refund(false, "30.00");   // an older event arriving late

        assertThat(card.getBalance()).isEqualByComparingTo("50.00");
        assertThat(card.getRefundedAmount()).isEqualByComparingTo("50.00");
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void partialRefundLargerThanWhatIsLeftEmptiesTheCardOnly() {
        // Holder already spent 80 of 100; the salon refunds 50 of the purchase.
        GiftCard card = card(PaymentStatus.PAID, GiftCardStatus.ACTIVE, "20.00");
        card.setOwner(User.builder().id(UUID.randomUUID()).build());

        refund(false, "50.00");

        assertThat(card.getBalance()).isEqualByComparingTo("0");
        assertThat(card.getStatus()).isEqualTo(GiftCardStatus.DEPLETED);   // owned card: spent down
        assertThat(card.getRefundedAmount()).isEqualByComparingTo("50.00");
        assertThat(onlyLedgerRow().getAmount()).isEqualByComparingTo("-20.00");
    }

    @Test
    void partialRefundEmptyingAnUnredeemedCardCancelsIt() {
        GiftCard card = card(PaymentStatus.PAID, GiftCardStatus.ACTIVE, "100.00");

        refund(false, "100.00");   // partial flag but covers the whole amount

        assertThat(card.getBalance()).isEqualByComparingTo("0");
        assertThat(card.getStatus()).isEqualTo(GiftCardStatus.CANCELLED);
    }

    @Test
    void unknownPaymentIntentIsIgnored() {
        when(giftCardRepository.findByStripePaymentIntentId("pi_unknown")).thenReturn(Optional.empty());

        service.onStripeWebhook(new GiftCardStripeWebhookEvent("pi_unknown", "charge.refunded", true, null));

        verify(giftCardRepository, never()).save(any());
        verifyNoInteractions(transactionRepository, eventPublisher);
    }
}
