package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.verdant.salon_ecomm.dtos.giftcards.*;
import com.verdant.salon_ecomm.dtos.giftcards.events.*;
import com.verdant.salon_ecomm.exceptions.PaymentException;
import com.verdant.salon_ecomm.mappers.GiftCardMapper;
import com.verdant.salon_ecomm.models.entities.giftcards.GiftCard;
import com.verdant.salon_ecomm.models.entities.giftcards.GiftCardTransaction;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.giftcards.GiftCardStatus;
import com.verdant.salon_ecomm.models.enums.giftcards.GiftCardTransactionType;
import com.verdant.salon_ecomm.repositories.GiftCardRepository;
import com.verdant.salon_ecomm.repositories.GiftCardTransactionRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;
import com.verdant.salon_ecomm.utils.InputValidators;

@Service
@RequiredArgsConstructor
@Slf4j
public class GiftCardService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Set<AccountRole> ISSUER_ROLES =
        EnumSet.of(AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.OWNER);

    // Business limits - adjust to your policy.
    private static final BigDecimal MIN_PURCHASE = new BigDecimal("5.00");
    private static final BigDecimal MAX_PURCHASE = new BigDecimal("1000.00");
    private static final BigDecimal MAX_ADMIN_ISSUE = new BigDecimal("5000.00");
    private static final int MAX_NAME_LENGTH = 100;     // matches recipient_name column
    private static final int MAX_NOTE_LENGTH = 500;
    private static final int ABANDONED_AFTER_HOURS = 24;
    private static final String INVALID_CODE_MESSAGE = "This gift card code is invalid or can't be redeemed.";

    private final GiftCardRepository giftCardRepository;
    private final GiftCardTransactionRepository giftCardTransactionRepository;
    private final UserRepository userRepository;
    private final GiftCardMapper giftCardMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentService paymentService;
    private final GiftCardStripeGateway stripeGateway;
    private final GiftCardRedeemLimiter redeemLimiter;
    private final TransactionTemplate transactionTemplate;

    private static final int SWEEP_BATCH_SIZE = 200;
    private static final int MAX_CODE_ATTEMPTS = 10;

    // ── Purchase ─────────────────────────────────────────────────
    // Deliberately NOT @Transactional: the card row is committed first, the Stripe call happens
    // outside any DB transaction, then the intent id is saved in a second short transaction.
    // A Stripe failure can no longer leave an orphaned PaymentIntent behind a rolled-back card.

    public GiftCardPaymentDto purchaseGiftCard(UUID purchaserId, PurchaseGiftCardInput input) {
        BigDecimal amount = validateAmount(input.amount(), MIN_PURCHASE, MAX_PURCHASE);
        String recipientName = cleanText(input.recipientName(), MAX_NAME_LENGTH, "Recipient name");
        String recipientEmail = cleanEmail(input.recipientEmail());
        if (recipientEmail == null) {
            throw BusinessRuleException.invalid("Recipient email is required");
        }

        String note = cleanText(input.note(), MAX_NOTE_LENGTH, "Note");

        User purchaser = userRepository.findById(purchaserId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String stripeCustomerId = paymentService.ensureStripeCustomer(purchaser);

        GiftCard created = transactionTemplate.execute(status -> giftCardRepository.save(
            GiftCard.builder()
                .code(generateUniqueCode())
                .balance(amount)
                .initialAmount(amount)
                .status(GiftCardStatus.ACTIVE)
                .paymentStatus(PaymentStatus.PENDING)
                .purchaser(purchaser)
                .recipientName(recipientName)
                .recipientEmail(recipientEmail)
                .note(note)
                .build()
        ));

        if (created == null) {
            throw new IllegalStateException("Gift card purchase could not be saved");
        }

        PaymentIntent intent;
        try {
            intent = stripeGateway.createPaymentIntent(created, stripeCustomerId);
        } catch (StripeException ex) {
            transactionTemplate.executeWithoutResult(status -> giftCardRepository.findById(created.getId())
                .ifPresent(card -> {
                    card.setStatus(GiftCardStatus.CANCELLED);
                    card.setPaymentStatus(PaymentStatus.FAILED);
                    giftCardRepository.save(card);
                }));
            throw new PaymentException("Failed to create PaymentIntent for gift card " + created.getId(), ex);
        }

        GiftCard saved = transactionTemplate.execute(status -> {
            GiftCard card = giftCardRepository.findById(created.getId()).orElseThrow();
            card.setStripePaymentIntentId(intent.getId());
            return giftCardRepository.save(card);
        });

        // No GiftCardPurchasedEvent / PURCHASE ledger row yet - nothing has been paid.
        // Both are produced by markPaid() when the Stripe webhook confirms payment.
        // M-18: masked code, same as getMySentGiftCards. The full code returned here let the buyer redeem
        // the card into their own wallet before the recipient got it. Only the recipient receives the code,
        // by email (GiftCardEmailListener, once payment is confirmed).
        return new GiftCardPaymentDto(giftCardMapper.toMaskedDto(saved), intent.getClientSecret());
    }

    // ── Stripe webhook (routed from PaymentService via event) ────

    @EventListener
    @Transactional
    public void onStripeWebhook(GiftCardStripeWebhookEvent event) {
        GiftCard card = giftCardRepository.findByStripePaymentIntentId(event.paymentIntentId()).orElse(null);
        if (card == null) {
            log.warn("Received webhook {} for unknown PaymentIntent {}", event.eventType(), event.paymentIntentId());
            return;
        }

        switch (event.eventType()) {
            case "payment_intent.succeeded" -> markPaid(card);
            case "payment_intent.payment_failed" -> {
                // Not terminal: Stripe lets the customer retry the same intent, so only record the failure.
                if (card.getPaymentStatus() != PaymentStatus.PAID) {
                    card.setPaymentStatus(PaymentStatus.FAILED);
                    giftCardRepository.save(card);
                }
            }
            case "payment_intent.canceled" -> {
                if (card.getPaymentStatus() != PaymentStatus.PAID) {
                    card.setPaymentStatus(PaymentStatus.CANCELLED);
                    card.setStatus(GiftCardStatus.CANCELLED);
                    giftCardRepository.save(card);
                }
            }
            // M-14: charge.refunded also fires for PARTIAL refunds. Only a full refund voids the card;
            // a partial one takes the newly refunded part off the balance.
            case "charge.refunded" -> {
                if (Boolean.FALSE.equals(event.fullRefund()) && event.amountRefunded() != null) {
                    applyPartialRefund(card, event.amountRefunded());
                } else {
                    voidPaidCard(card, event.eventType());
                }
            }
            // M-13: this case never ran before - PaymentService couldn't read the PaymentIntent id off a
            // Dispute, so chargebacks were dropped. The card is voided as soon as the dispute opens:
            // its balance is spendable immediately, so waiting for the outcome would let it be drained.
            // A dispute the salon later wins does not reinstate the card; staff reissue it by hand.
            case "charge.dispute.created" -> voidPaidCard(card, event.eventType());
            default -> log.debug("Ignoring Stripe event {} for gift card {}", event.eventType(), card.getId());
        }
    }

    private void markPaid(GiftCard card) {
        if (card.getPaymentStatus() == PaymentStatus.PAID) return; // already handled (duplicate delivery)

        // A voided/refunded card must never be revived by a late or repeated webhook.
        // PARTIALLY_REFUNDED counts too (M-14): it was paid already, and re-marking it PAID would write
        // a second PURCHASE row and email the code again.
        if (card.getPaymentStatus() == PaymentStatus.REFUNDED
            || card.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            log.warn("Ignoring payment success for gift card {} - it was already refunded/voided", card.getId());
            return;
        }

        if (card.getStatus() == GiftCardStatus.CANCELLED) {
            // Only abandoned/unpaid cards get here (payment status PENDING, FAILED or CANCELLED).
            // Money was taken, so honor the purchase.
            log.warn("Gift card {} was paid after being cancelled - reinstating", card.getId());
            card.setStatus(GiftCardStatus.ACTIVE);
        }

        card.setPaymentStatus(PaymentStatus.PAID);
        giftCardRepository.save(card);

        writeTransaction(card, GiftCardTransactionType.PURCHASE, card.getInitialAmount(),
            "Purchase paid " + card.maskedCode(), null);

        // Listeners (after commit): purchaser/staff notification, audit, and GiftCardEmailListener,
        // which emails the code to recipientEmail.
        eventPublisher.publishEvent(new GiftCardPurchasedEvent(card, card.getPurchaser()));
    }

    private void voidPaidCard(GiftCard card, String reason) {
        // PARTIALLY_REFUNDED: a full refund (or chargeback) after an earlier partial one still voids it.
        if (card.getPaymentStatus() != PaymentStatus.PAID
            && card.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED) return;
        BigDecimal remaining = card.getBalance();
        if (remaining.signum() > 0) {
            writeTransaction(card, GiftCardTransactionType.REFUND, remaining.negate(),
                "Voided: " + reason, null);
        }
        card.setBalance(BigDecimal.ZERO);
        card.setStatus(GiftCardStatus.CANCELLED);
        card.setPaymentStatus(PaymentStatus.REFUNDED);
        giftCardRepository.save(card);
        log.warn("Gift card {} voided by {} - {} of balance removed (any amount already spent is NOT recoverable here)",
            card.getId(), reason, remaining);
    }

    // M-14 flow: delta = Stripe's refunded-so-far total minus what this card already had deducted
    // (refundedAmount) -> take min(delta, balance) off the balance with a REFUND ledger row -> remember
    // the new total -> mark PARTIALLY_REFUNDED. The card stays usable for what is left.
    // Duplicate or out-of-order deliveries give delta <= 0 and change nothing.
    // If the holder already spent more than is left, the shortfall can't be taken back here (same as a
    // full void): it is logged for staff.
    private void applyPartialRefund(GiftCard card, BigDecimal refundedSoFar) {
        if (card.getPaymentStatus() != PaymentStatus.PAID
            && card.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED) return;

        BigDecimal alreadyDeducted = card.getRefundedAmount() != null ? card.getRefundedAmount() : BigDecimal.ZERO;
        BigDecimal delta = refundedSoFar.subtract(alreadyDeducted);
        if (delta.signum() <= 0) return;

        BigDecimal deducted = delta.min(card.getBalance());
        if (deducted.signum() > 0) {
            writeTransaction(card, GiftCardTransactionType.REFUND, deducted.negate(),
                "Partial refund of purchase " + card.maskedCode(), null);
            card.setBalance(card.getBalance().subtract(deducted));
            if (card.getBalance().signum() == 0) {
                // Nothing left: an owned card is spent down; an unredeemed one has nothing to redeem.
                card.setStatus(card.getOwner() != null ? GiftCardStatus.DEPLETED : GiftCardStatus.CANCELLED);
            }
        }
        if (deducted.compareTo(delta) < 0) {
            log.warn("Gift card {} partially refunded by {} but only {} was left on it - {} already spent is NOT recoverable here",
                card.getId(), delta, deducted, delta.subtract(deducted));
        }

        card.setRefundedAmount(refundedSoFar);
        card.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        giftCardRepository.save(card);
    }

    // ── Admin / manager manual issuance ──────────────────────────

    @Transactional
    public GiftCardDto adminIssueGiftCard(UUID actorId, AdminIssueGiftCardInput input) {
        User actor = userRepository.findById(actorId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if(!ISSUER_ROLES.contains(actor.getRole())) {
            throw new AccessDeniedException("Only managers and admins can issue gift cards");
        }

        BigDecimal amount = validateAmount(input.amount(), new BigDecimal("0.01"), MAX_ADMIN_ISSUE);
        String recipientName = cleanText(input.recipientName(), MAX_NAME_LENGTH, "Recipient name");
        String recipientEmail = cleanEmail(input.recipientEmail());
        String note = cleanText(input.note(), MAX_NOTE_LENGTH, "Note");
        if (input.expiresAt() != null && !input.expiresAt().isAfter(OffsetDateTime.now())) {
            throw BusinessRuleException.invalid("Expiry date must be in the future");
        }

        GiftCard giftCard = giftCardRepository.save(GiftCard.builder()
            .code(generateUniqueCode())
            .balance(amount)
            .initialAmount(amount)
            .status(GiftCardStatus.ACTIVE)
            .paymentStatus(PaymentStatus.PAID) // no charge involved
            .recipientName(recipientName)
            .recipientEmail(recipientEmail)
            .note(note)
            .expiresAt(input.expiresAt())
            .build());

        writeTransaction(giftCard, GiftCardTransactionType.ADMIN_ADJUSTMENT, amount,
            "Issued by " + actor.getFullName(), null);

        eventPublisher.publishEvent(new GiftCardIssuedByAdminEvent(giftCard, actor));
        return giftCardMapper.toDto(giftCard);
    }

    // ── Redeem into wallet ───────────────────────────────────────

    @Transactional
    public GiftCardDto redeemGiftCard(UUID userId, String rawCode) {
        redeemLimiter.checkAllowed(userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        GiftCard giftCard = code.isEmpty() ? null : giftCardRepository.findByCodeForUpdate(code).orElse(null);

        // One generic message for every failure reason, so codes can't be probed for state.
        if (giftCard == null || !isRedeemable(giftCard)) {
            redeemLimiter.recordFailure(userId);
            throw BusinessRuleException.invalid(INVALID_CODE_MESSAGE);
        }

        giftCard.setOwner(user);
        giftCard.setStatus(GiftCardStatus.REDEEMED);
        giftCard = giftCardRepository.save(giftCard);

        // Ownership change only - no money moves, so the ledger amount is zero.
        writeTransaction(giftCard, GiftCardTransactionType.REDEMPTION, BigDecimal.ZERO,
            "Redeemed " + giftCard.maskedCode(), null);

        redeemLimiter.reset(userId);
        eventPublisher.publishEvent(new GiftCardRedeemedEvent(giftCard, user));
        return giftCardMapper.toDto(giftCard);
    }

    // A partially refunded card can still be redeemed for what is left on it (M-14).
    private boolean isRedeemable(GiftCard card) {
        return card.getStatus() == GiftCardStatus.ACTIVE
            && (card.getPaymentStatus() == PaymentStatus.PAID
                || card.getPaymentStatus() == PaymentStatus.PARTIALLY_REFUNDED)
            && card.getOwner() == null
            && (card.getExpiresAt() == null || card.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    // ── Reads ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GiftCardDto> getMyGiftCards(UUID userId) {
        return giftCardRepository.findByOwnerIdOrderByCreatedAtDesc(userId).stream()
            .map(giftCardMapper::toDto)
            .toList();
    }

    // Cards the user bought for someone else. Codes are masked - the buyer shouldn't be able to spend them.
    @Transactional(readOnly = true)
    public List<GiftCardDto> getMySentGiftCards(UUID userId) {
        return giftCardRepository.findByPurchaserIdOrderByCreatedAtDesc(userId).stream()
            .map(giftCardMapper::toMaskedDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal getMyWalletBalance(UUID userId) {
        return giftCardRepository.getRedeemableBalance(userId);
    }

    @Transactional(readOnly = true)
    public List<GiftCardTransactionDto> getMyTransactions(UUID userId) {
        return giftCardTransactionRepository.findByOwnerIdOrderByCreatedAtDesc(userId).stream()
            .map(giftCardMapper::toDto)
            .toList();
    }

    // ── Checkout drawdown - called from OrderService.placeOrder ──

    //Drains the user's redeemed, unexpired gift cards (soonest-expiring first) against {@code amountRequested}.
    //Does NOT touch Stripe - caller sends any shortfall there. Runs inside the order transaction.
    @Transactional
    public GiftCardBalanceApplicationResult applyBalanceToOrder(UUID userId, BigDecimal amountRequested, Order order) {
        if (amountRequested == null || amountRequested.signum() <= 0) {
            return new GiftCardBalanceApplicationResult(BigDecimal.ZERO, List.of());
        }

        List<GiftCard> cards = giftCardRepository.findSpendableCardsForUpdate(userId);
        BigDecimal remaining = amountRequested;
        BigDecimal totalApplied = BigDecimal.ZERO;
        List<GiftCardTransaction> txs = new ArrayList<>();

        for (GiftCard card : cards) {
            if (remaining.signum() <= 0) break;
            BigDecimal draw = card.getBalance().min(remaining);
            if (draw.signum() <= 0) continue;

            card.setBalance(card.getBalance().subtract(draw));
            if (card.getBalance().signum() == 0) {
                card.setStatus(GiftCardStatus.DEPLETED);
            }
            giftCardRepository.save(card);

            txs.add(writeTransaction(card, GiftCardTransactionType.ORDER_PAYMENT,
                draw.negate(), "Applied to order " + order.getOrderCode(), order));

            remaining = remaining.subtract(draw);
            totalApplied = totalApplied.add(draw);
        }

        if (totalApplied.signum() > 0) {
            eventPublisher.publishEvent(new GiftCardAppliedToOrderEvent(order, order.getUser(), txs, totalApplied));
        }
        return new GiftCardBalanceApplicationResult(totalApplied, txs.stream().map(giftCardMapper::toDto).toList());
    }

    // ── Give wallet money back when an order's payment is canceled (not on FAILED: retryable, H-3) ──

    @EventListener
    @Transactional
    public void onOrderPaymentVoided(OrderPaymentVoidedEvent event) {
        restoreWalletForOrder(event.order());
    }

    // Idempotent. Call from the admin-cancel path in OrderService too.
    // M-12 flow, per ORDER_PAYMENT row of the order: lock the card -> voided? skip it and publish
    // GiftCardRestoreBlockedEvent (audit entry for admin review) -> otherwise add the amount back,
    // set the status, write a REFUND ledger row.
    // Before, the status was set to REDEEMED/EXPIRED unconditionally, which revived voided cards: buy a
    // card, spend it on an order, get the card purchase refunded (card voided), then cancel the order
    // and the balance came back on a spendable card. The customer kept the refund AND the balance.
    @Transactional
    public void restoreWalletForOrder(Order order) {
        if (order.getWalletAmountApplied() == null || order.getWalletAmountApplied().signum() <= 0) return;
        if (giftCardTransactionRepository.existsByOrderIdAndType(order.getId(), GiftCardTransactionType.REFUND)) return;

        OffsetDateTime now = OffsetDateTime.now();
        for (GiftCardTransaction payment : giftCardTransactionRepository
            .findByOrderIdAndType(order.getId(), GiftCardTransactionType.ORDER_PAYMENT)) {

            GiftCard card = giftCardRepository.findByIdForUpdate(payment.getGiftCard().getId()).orElseThrow();
            BigDecimal restored = payment.getAmount().negate(); // ORDER_PAYMENT amounts are stored negative

            if (!canReceiveRestoredBalance(card)) {
                log.warn("Not restoring {} from order {} to voided gift card {} (status {}, payment {}) - flagged for review",
                    restored, order.getOrderCode(), card.getId(), card.getStatus(), card.getPaymentStatus());
                eventPublisher.publishEvent(new GiftCardRestoreBlockedEvent(card, order, restored));
                continue;
            }

            card.setBalance(card.getBalance().add(restored));
            // Only a card that was spendable (REDEEMED) or spent down (DEPLETED) goes back to REDEEMED.
            // An EXPIRED card stays EXPIRED: the money is recorded on it but can't be spent.
            boolean expired = card.getStatus() == GiftCardStatus.EXPIRED
                || (card.getExpiresAt() != null && card.getExpiresAt().isBefore(now));
            card.setStatus(expired ? GiftCardStatus.EXPIRED : GiftCardStatus.REDEEMED);
            giftCardRepository.save(card);

            writeTransaction(card, GiftCardTransactionType.REFUND, restored,
                "Restored from order " + order.getOrderCode(), order);
        }
        order.setWalletAmountApplied(BigDecimal.ZERO); // managed entity - flushed with the caller's transaction
    }

    // M-12: money may only flow back to a card that could legitimately have paid for the order.
    // CANCELLED = voided (Stripe refund or chargeback, abandoned purchase, forfeited on account deletion);
    // REFUNDED = its purchase money already went back to the buyer. ACTIVE (never redeemed) can't have
    // paid for an order, so it is treated as suspicious too.
    private boolean canReceiveRestoredBalance(GiftCard card) {
        if (card.getPaymentStatus() == PaymentStatus.REFUNDED) return false;
        return card.getStatus() == GiftCardStatus.REDEEMED
            || card.getStatus() == GiftCardStatus.DEPLETED
            || card.getStatus() == GiftCardStatus.EXPIRED;
    }

    // Call before hard-deleting orders so gift_card_transactions.order_id doesn't block the delete operation.
    @Transactional
    public void detachOrderReferences(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return;
        giftCardTransactionRepository.detachOrders(orderIds);
    }

    // ── Delete-account forfeiture → refund-in-kind ───────────────

    @Transactional
    public void forfeitAndRefundOnAccountDeletion(User departingUser, String refundDeliveryEmail) {
        List<GiftCard> cards = giftCardRepository.findSpendableCardsForUpdate(departingUser.getId());
        BigDecimal total = cards.stream().map(GiftCard::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.signum() > 0) {
            for (GiftCard card : cards) {
                writeTransaction(card, GiftCardTransactionType.REFUND, card.getBalance().negate(),
                    "Forfeited on account deletion", null);
                card.setBalance(BigDecimal.ZERO);
                card.setStatus(GiftCardStatus.CANCELLED);
                giftCardRepository.save(card);
            }

            GiftCard replacement = giftCardRepository.save(GiftCard.builder()
                .code(generateUniqueCode())
                .balance(total)
                .initialAmount(total)
                .status(GiftCardStatus.ACTIVE)
                .paymentStatus(PaymentStatus.PAID)
                .recipientEmail(refundDeliveryEmail)
                .note("Refund for deleted account balance")
                .build());
            writeTransaction(replacement, GiftCardTransactionType.REFUND, total, "Account-deletion refund", null);

            // GiftCardEmailListener emails replacement.getCode() to refundDeliveryEmail after commit.
            // The account is anonymized, so that email is the ONLY way the user ever receives the code.
            eventPublisher.publishEvent(new GiftCardForfeitedEvent(cards.getFirst(), departingUser, total, true, replacement));
        }
    }

    // ── Scheduled sweeps - run hourly by GiftCardSweepJob ──

    // Expires ACTIVE and REDEEMED cards past expiry:
    // zeroes the balance and writes an EXPIRY ledger row.
    // Deliberately not @Transactional: each batch commits in its own transactionTemplate call.
    public int sweepExpiredCards() {
        int total = 0;
        int processed;
        do {
            Integer n = transactionTemplate.execute(status -> {
                List<GiftCard> batch = giftCardRepository.findExpirable(
                    List.of(GiftCardStatus.ACTIVE, GiftCardStatus.REDEEMED),
                    OffsetDateTime.now(), PageRequest.of(0, SWEEP_BATCH_SIZE));
                for (GiftCard card : batch) {
                    BigDecimal forfeited = card.getBalance();
                    writeTransaction(card, GiftCardTransactionType.EXPIRY, forfeited.negate(),
                        "Expired " + card.maskedCode(), null);
                    card.setBalance(BigDecimal.ZERO);
                    card.setStatus(GiftCardStatus.EXPIRED);
                    giftCardRepository.save(card);
                    eventPublisher.publishEvent(
                        new GiftCardForfeitedEvent(
                            card,
                            card.getOwner(),
                            forfeited,
                            false,
                            null
                        )
                    );
                }
                return batch.size();
            });
            processed = n == null ? 0 : n;
            total += processed;
        } while (processed == SWEEP_BATCH_SIZE);
        return total;
    }

    // Cancels purchases that were never paid (PENDING/FAILED for 24h+) and their Stripe intents.
    // Deliberately not @Transactional: an outer transaction would make every transactionTemplate call
    // join it, putting the Stripe calls below back inside an open transaction.
    public int cancelAbandonedPurchases() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(ABANDONED_AFTER_HOURS);
        int total = 0;
        int processed;
        do {
            List<String> intentIds = new ArrayList<>();
            Integer n = transactionTemplate.execute(status -> {
                List<GiftCard> batch = giftCardRepository.findAbandonedPurchases(
                    GiftCardStatus.ACTIVE, List.of(PaymentStatus.PENDING, PaymentStatus.FAILED),
                    cutoff, PageRequest.of(0, SWEEP_BATCH_SIZE));
                for (GiftCard card : batch) {
                    card.setStatus(GiftCardStatus.CANCELLED);
                    card.setPaymentStatus(PaymentStatus.CANCELLED);
                    giftCardRepository.save(card);
                    if (card.getStripePaymentIntentId() != null) intentIds.add(card.getStripePaymentIntentId());
                }
                return batch.size();
            });
            intentIds.forEach(stripeGateway::cancelPaymentIntent); // after the batch commits, outside any transaction
            processed = n == null ? 0 : n;
            total += processed;
        } while (processed == SWEEP_BATCH_SIZE);
        return total;
    }

    // ── Helpers ──────────────────────────────────────────────────

    private GiftCardTransaction writeTransaction(
        GiftCard card, GiftCardTransactionType type,
        BigDecimal amount, String description, Order order
    ) {
        return giftCardTransactionRepository.save(GiftCardTransaction.builder()
            .giftCard(card)
            .type(type)
            .amount(amount)
            .description(description.length() > 255 ? description.substring(0, 255) : description)
            .order(order)
            .build());
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            StringBuilder sb = new StringBuilder("VLX-");
            for (int i = 0; i < 8; i++) {
                if (i == 4) sb.append('-');
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = sb.toString();
            if (!giftCardRepository.existsByCode(code)) return code;
        }
        throw new IllegalStateException("Could not generate a unique gift card code");
    }

    private BigDecimal validateAmount(BigDecimal amount, BigDecimal min, BigDecimal max) {
        if (amount == null) throw BusinessRuleException.invalid("Amount is required");
        if (amount.stripTrailingZeros().scale() > 2) {
            throw BusinessRuleException.invalid("Amount can have at most 2 decimal places");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.compareTo(min) < 0 || normalized.compareTo(max) > 0) {
            throw BusinessRuleException.invalid("Amount must be between " + min + " and " + max);
        }
        return normalized;
    }

    private String cleanText(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw BusinessRuleException.invalid(label + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private String cleanEmail(String value) {
        String trimmed = cleanText(value, 254, "Recipient email");
        if (trimmed != null && !InputValidators.isEmail(trimmed)) {
            throw BusinessRuleException.invalid("Recipient email is not a valid email address");
        }
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}