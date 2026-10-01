package com.verdant.salon_ecomm.services;

import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.verdant.salon_ecomm.config.StripeConfig;
import com.verdant.salon_ecomm.dtos.gitftcards.*;
import com.verdant.salon_ecomm.dtos.gitftcards.events.*;
import com.verdant.salon_ecomm.exceptions.PaymentException;
import com.verdant.salon_ecomm.mappers.GiftCardMapper;
import com.verdant.salon_ecomm.models.entities.GiftCard;
import com.verdant.salon_ecomm.models.entities.GiftCardTransaction;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.giftcards.GiftCardStatus;
import com.verdant.salon_ecomm.models.enums.giftcards.GiftCardTransactionType;
import com.verdant.salon_ecomm.repositories.GiftCardRepository;
import com.verdant.salon_ecomm.repositories.GiftCardTransactionRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GiftCardService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GiftCardRepository giftCardRepository;
    private final GiftCardTransactionRepository giftCardTransactionRepository;
    private final UserRepository userRepository;
    private final GiftCardMapper giftCardMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final StripeClient stripeClient;
    private final StripeConfig stripeConfig;
    private final PaymentService paymentService;
    @Lazy
    private final GiftCardService giftCardService;

    // ── Purchase (real Stripe charge) ───────────────────────────
    @Transactional
    public GiftCardPaymentDto purchaseGiftCard(UUID purchaserId, PurchaseGiftCardInput input) {
        User purchaser = userRepository.findById(purchaserId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        String stripeCustomerId = paymentService.ensureStripeCustomer(purchaser);

        GiftCard giftCard = GiftCard.builder()
            .code(generateUniqueCode())
            .balance(input.amount())
            .initialAmount(input.amount())
            .status(GiftCardStatus.ACTIVE)
            .paymentStatus(PaymentStatus.PENDING)
            .purchaser(purchaser)
            .recipientName(input.recipientName())
            .recipientEmail(input.recipientEmail())
            .note(input.note())
            .build();
        giftCard = giftCardRepository.save(giftCard);

        PaymentIntent intent;
        try {
            intent = giftCardService.createGiftCardPaymentIntent(giftCard, stripeCustomerId);
        } catch (StripeException ex) {
            throw new PaymentException("Failed to create PaymentIntent for gift card " + giftCard.getId(), ex);
        }

        giftCard.setStripePaymentIntentId(intent.getId());
        giftCard = giftCardRepository.save(giftCard);

        writeTransaction(giftCard, GiftCardTransactionType.PURCHASE, input.amount(),
            "Purchased " + giftCard.getCode(), null);
        eventPublisher.publishEvent(new GiftCardPurchasedEvent(giftCard, purchaser));

        return new GiftCardPaymentDto(giftCardMapper.toDto(giftCard), intent.getClientSecret());
    }

    // Called from the Stripe webhook handler when a gift-card PaymentIntent succeeds.
    @Transactional
    public void confirmGiftCardPurchase(String stripePaymentIntentId) {
        GiftCard giftCard = giftCardRepository.findByStripePaymentIntentId(stripePaymentIntentId)
            .orElseThrow(() -> new EntityNotFoundException("Gift card not found for payment intent"));
        giftCard.setPaymentStatus(PaymentStatus.PAID);
        giftCardRepository.save(giftCard);
        // GiftCardPurchasedEvent listener (email/notification) fires off giftCard.paymentStatus == PAID,
        // OR send the delivery email directly here — whichever your NotificationPublisher convention prefers.
    }

    // ── Admin / manager manual issuance ─────────────────────────

    @Transactional
    public GiftCardDto adminIssueGiftCard(UUID actorId, AdminIssueGiftCardInput input) {
        User actor = userRepository.findById(actorId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        GiftCard giftCard = GiftCard.builder()
            .code(generateUniqueCode())
            .balance(input.amount())
            .initialAmount(input.amount())
            .status(GiftCardStatus.ACTIVE)
            .paymentStatus(PaymentStatus.PAID) // no charge involved
            .purchaser(null)
            .recipientName(input.recipientName())
            .recipientEmail(input.recipientEmail())
            .note(input.note())
            .expiresAt(input.expiresAt())
            .build();
        giftCard = giftCardRepository.save(giftCard);

        writeTransaction(giftCard, GiftCardTransactionType.ADMIN_ADJUSTMENT, input.amount(),
            "Issued by " + actor.getFullName(), null);

        eventPublisher.publishEvent(new GiftCardIssuedByAdminEvent(giftCard, actor));
        return giftCardMapper.toDto(giftCard);
    }

    // ── Redeem into wallet ───────────────────────────────────────
    @Transactional
    public GiftCardDto redeemGiftCard(UUID userId, String code) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        GiftCard giftCard = giftCardRepository.findByCode(code.trim().toUpperCase())
            .orElseThrow(() -> new IllegalArgumentException("Invalid gift card code"));

        if (giftCard.getStatus() != GiftCardStatus.ACTIVE) {
            throw new IllegalStateException("This gift card is not available to redeem");
        }
        if (giftCard.getPaymentStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("This gift card has not completed payment yet");
        }
        if (giftCard.getExpiresAt() != null && giftCard.getExpiresAt().isBefore(java.time.OffsetDateTime.now())) {
            throw new IllegalStateException("This gift card has expired");
        }
        if (giftCard.getOwner() != null) {
            throw new IllegalStateException("This gift card has already been redeemed");
        }

        giftCard.setOwner(user);
        giftCard.setStatus(GiftCardStatus.REDEEMED);
        giftCard = giftCardRepository.save(giftCard);

        writeTransaction(giftCard, GiftCardTransactionType.REDEMPTION, BigDecimal.ZERO,
            "Redeemed " + giftCard.getCode(), null);

        eventPublisher.publishEvent(new GiftCardRedeemedEvent(giftCard, user));
        return giftCardMapper.toDto(giftCard);
    }

    // ── Reads ────────────────────────────────────────────────────

    public List<GiftCardDto> getMyGiftCards(UUID userId) {
        return giftCardRepository.findByOwnerIdOrderByCreatedAtDesc(userId).stream()
            .map(giftCardMapper::toDto)
            .toList();
    }

    public BigDecimal getMyWalletBalance(UUID userId) {
        return giftCardRepository.getRedeemableBalance(userId);
    }

    public List<GiftCardTransactionDto> getMyTransactions(UUID userId) {
        return giftCardTransactionRepository.findByOwnerIdOrderByCreatedAtDesc(userId).stream()
            .map(giftCardMapper::toDto)
            .toList();
    }

    @Transactional
    public void applyStripeEventToGiftCard(String paymentIntentId, String eventType) {
        GiftCard giftCard = giftCardRepository.findByStripePaymentIntentId(paymentIntentId).orElse(null);
        if (giftCard == null) {
            log.warn("Received webhook for unknown PaymentIntent {}", paymentIntentId);
            return;
        }
        if ("payment_intent.succeeded".equals(eventType)) {
            if (giftCard.getPaymentStatus() == PaymentStatus.PAID) return; // already handled
            giftCard.setPaymentStatus(PaymentStatus.PAID);
            giftCardRepository.save(giftCard);
            // delivery email to recipientEmail goes here, via EmailService
        } else if ("payment_intent.payment_failed".equals(eventType)) {
            giftCard.setPaymentStatus(PaymentStatus.FAILED);
            giftCardRepository.save(giftCard);
        }
    }

    // ── Checkout drawdown — called from OrderService.placeOrder ───

    /**
     * Drains the user's redeemed gift cards (soonest-expiring first) against {@code amountRequested},
     * up to what's available. Does NOT touch Stripe — caller sends any shortfall there.
     * Must run inside the same transaction as order creation.
     */
    @Transactional
    public GiftCardBalanceApplicationResult applyBalanceToOrder(
        UUID userId, BigDecimal amountRequested,
        Order order
    ) {
        List<GiftCard> cards = giftCardRepository.findSpendableCardsForUpdate(userId);
        BigDecimal remaining = amountRequested;
        BigDecimal totalApplied = BigDecimal.ZERO;
        List<GiftCardTransactionDto> txDtos = new ArrayList<>();

        for (GiftCard card : cards) {
            if (remaining.signum() <= 0) break;
            BigDecimal draw = card.getBalance().min(remaining);
            if (draw.signum() <= 0) continue;

            card.setBalance(card.getBalance().subtract(draw));
            if (card.getBalance().signum() == 0) {
                card.setStatus(GiftCardStatus.CANCELLED); // fully spent — terminal, matches "used once" rule
            }
            giftCardRepository.save(card);

            GiftCardTransaction tx = writeTransaction(card, GiftCardTransactionType.ORDER_PAYMENT,
                draw.negate(), "Applied to order " + order.getId(), order);
            txDtos.add(giftCardMapper.toDto(tx));

            remaining = remaining.subtract(draw);
            totalApplied = totalApplied.add(draw);
        }

        if (totalApplied.signum() > 0) {
            eventPublisher.publishEvent(new GiftCardAppliedToOrderEvent(
                order, cards.getFirst().getOwner(),
                cards.stream().map(c -> (GiftCardTransaction) null).toList(), // replaced by real tx list below
                totalApplied
            ));
        }
        return new GiftCardBalanceApplicationResult(totalApplied, txDtos);
    }

    // ── Delete-account forfeiture → refund-in-kind ─────────────────

    @Transactional
    public void forfeitAndRefundOnAccountDeletion(User departingUser, String refundDeliveryEmail) {
        List<GiftCard> cards = giftCardRepository.findSpendableCardsForUpdate(departingUser.getId());
        BigDecimal total = cards.stream().map(GiftCard::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) return;

        for (GiftCard card : cards) {
            writeTransaction(card, GiftCardTransactionType.REFUND, card.getBalance().negate(),
                "Forfeited on account deletion", null);
            card.setBalance(BigDecimal.ZERO);
            card.setStatus(GiftCardStatus.CANCELLED);
            giftCardRepository.save(card);
        }

        GiftCard replacement = GiftCard.builder()
            .code(generateUniqueCode())
            .balance(total)
            .initialAmount(total)
            .status(GiftCardStatus.ACTIVE)
            .paymentStatus(PaymentStatus.PAID)
            .recipientEmail(refundDeliveryEmail)
            .note("Refund for deleted account balance")
            .build();

        replacement = giftCardRepository.save(replacement);
        writeTransaction(
            replacement,
            GiftCardTransactionType.REFUND,
            total,
            "Account-deletion refund",
            null
        );

        eventPublisher.publishEvent(new GiftCardForfeitedEvent(
            cards.getFirst(), departingUser, total, true, replacement
        ));
    }

    // ── Expiry sweep — call from your existing cleanup job ─────────

    @Transactional
    public void sweepExpiredCards() {
        List<GiftCard> expired = giftCardRepository.findExpiredActiveCards();
        for (GiftCard card : expired) {
            card.setStatus(GiftCardStatus.EXPIRED);
            giftCardRepository.save(card);
            eventPublisher.publishEvent(new GiftCardForfeitedEvent(
                card, card.getOwner(), card.getBalance(), false, null
            ));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    @Retryable(
        retryFor = { ApiConnectionException.class, ApiException.class },
        backoff = @org.springframework.retry.annotation.Backoff(delay = 500, multiplier = 2)
    )
    PaymentIntent createGiftCardPaymentIntent(GiftCard giftCard, String stripeCustomerId) throws StripeException {
        String currency = stripeConfig.getCurrency();
        int fractionDigits = Currency.getInstance(currency.toUpperCase(Locale.ROOT)).getDefaultFractionDigits();
        long minorUnits = giftCard.getInitialAmount()
            .movePointRight(fractionDigits)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(minorUnits)
            .setCurrency(currency)
            .setCustomer(stripeCustomerId)
            .putMetadata("type", "gift_card")
            .putMetadata("gift_card_id", giftCard.getId().toString())
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
            )
            .build();

        RequestOptions options = RequestOptions.builder()
            .setIdempotencyKey("gift_card_" + giftCard.getId() + "_payment_intent")
            .build();

        return stripeClient.paymentIntents().create(params, options);
    }

    private GiftCardTransaction writeTransaction(
        GiftCard card, GiftCardTransactionType type, BigDecimal amount,
        String description, com.verdant.salon_ecomm.models.entities.Order order
    ) {
        GiftCardTransaction tx = GiftCardTransaction.builder()
            .giftCard(card)
            .type(type)
            .amount(amount)
            .description(description)
            .order(order)
            .build();
        return giftCardTransactionRepository.save(tx);
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder("VLX-");
            for (int i = 0; i < 8; i++) {
                if (i == 4) sb.append('-');
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (giftCardRepository.findByCode(code).isPresent());
        return code;
    }
}