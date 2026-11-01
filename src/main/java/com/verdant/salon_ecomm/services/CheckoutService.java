package com.verdant.salon_ecomm.services;

import com.stripe.model.PaymentIntent;
import com.verdant.salon_ecomm.dtos.checkout.CheckoutSessionDto;
import com.verdant.salon_ecomm.dtos.checkout.PlaceOrderResult;
import com.verdant.salon_ecomm.dtos.order.PlaceOrderInput;
import com.verdant.salon_ecomm.dtos.order.admin.AdminCreateOrderInput;
import com.verdant.salon_ecomm.dtos.payment.DraftPaymentSucceededEvent;
import com.verdant.salon_ecomm.dtos.payment.PaymentIntentDto;
import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.verdant.salon_ecomm.exceptions.CheckoutFailedException;
import com.verdant.salon_ecomm.exceptions.PaymentException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.PendingCheckout;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

// Order of events for a card checkout: draft saved (no order, no stock held) -> Stripe charge created ->
// customer pays -> order created from the draft. Deliberately not @Transactional: it calls Stripe, which must
// never run inside (or hold the locks of) a DB transaction. Each DB step is its own OrderService transaction.
//
// The order is created by two idempotent routes, whichever gets there first: finalizeCheckout (the browser,
// right after Stripe confirms, so the order exists for the "View order" button) and the payment_intent.succeeded
// webhook (backup for a closed tab). OrderService.createOrderFromDraft makes the second one a no-op.
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final UserRepository userRepository;

    // ---------- Start ----------

    public PlaceOrderResult placeOrder(UUID userId, PlaceOrderInput input) {
        replacePriorDrafts(userId);
        return start(orderService.createCustomerDraft(userId, input));
    }

    // Staff paying by card or bank on a customer's behalf (cash orders skip all this).
    public CheckoutSessionDto startAdminPayment(AdminCreateOrderInput input, User actor) {
        return start(orderService.createAdminDraft(input, actor)).checkout();
    }

    private PlaceOrderResult start(PendingCheckout draft) {
        // The gift-card wallet covers everything: nothing to charge, so the order is created and paid now.
        if (draft.getAmountDue().signum() <= 0) {
            return new PlaceOrderResult(createOrderOrRefund(draft.getId(), null), null);
        }

        String intentId = null;
        try {
            User customer = userRepository.findById(draft.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + draft.getUserId()));
            PaymentIntentDto intent = paymentService.createDraftPaymentIntent(draft, customer);
            intentId = intent.stripePaymentIntentId();
            orderService.attachPaymentIntent(draft.getId(), intentId);
            return new PlaceOrderResult(null, new CheckoutSessionDto(
                draft.getId(), intentId, intent.clientSecret(), intent.amount(), intent.currency()));
        } catch (RuntimeException ex) {
            // Nothing was charged yet, so drop the draft (and the intent, if Stripe already made one).
            if (intentId != null) paymentService.cancelPaymentIntentForExpiry(intentId);
            orderService.discardDraft(draft.getId());
            throw ex;
        }
    }

    // The customer opened the card form again (changed the bag, retried): the earlier drafts are dead weight.
    // An intent that can't be cancelled is already paid or processing; its draft stays for the webhook.
    private void replacePriorDrafts(UUID userId) {
        for (PendingCheckout old : orderService.findCustomerDrafts(userId)) {
            String intentId = old.getStripePaymentIntentId();
            if (intentId == null || paymentService.cancelPaymentIntentForExpiry(intentId)) {
                orderService.discardDraft(old.getId());
            }
        }
    }

    // ---------- Finish ----------

    // Called by the browser after Stripe confirms the card. Never trusts the browser: the payment is read
    // back from Stripe. If the webhook already made the order, that order is returned.
    public Order finalizeCheckout(String paymentIntentId, UUID userId, boolean isManagement) {
        Optional<PendingCheckout> draft = orderService.findDraftByPaymentIntent(paymentIntentId);
        if (draft.isPresent()) {
            PendingCheckout d = draft.get();
            boolean allowed = isManagement || userId.equals(d.getUserId()) || userId.equals(d.getCreatedByUserId());
            if (!allowed) throw new AccessDeniedException("Not your checkout");
            return completeDraft(d);
        }

        Order order = orderService.findOrderByPaymentIntent(paymentIntentId)
            .orElseThrow(() -> new ResourceNotFoundException("This checkout was not found or has expired."));
        boolean owner = order.getUser() != null && order.getUser().getId().equals(userId);
        if (!isManagement && !owner) throw new AccessDeniedException("Not your order");
        return order;
    }

    // Backup route: the webhook says a payment succeeded and no order has it yet.
    @EventListener
    public void onDraftPaymentSucceeded(DraftPaymentSucceededEvent event) {
        orderService.findDraftByPaymentIntent(event.paymentIntentId()).ifPresent(draft -> {
            try {
                completeDraft(draft);
            } catch (BusinessRuleException | ResourceNotFoundException ex) {
                // Refunded because the order couldn't be made, or the browser's route finished first. Either
                // way it is settled; an error here would only make Stripe resend the webhook for nothing.
                log.info("Draft {} not turned into an order by the webhook: {}", draft.getId(), ex.getMessage());
            }
        });
    }

    // For UnpaidOrderExpiryJob: an expired draft whose payment can no longer be cancelled was paid (or is being).
    public void settleExpiredDraft(PendingCheckout draft) {
        try {
            completeDraft(draft);
        } catch (BusinessRuleException | ResourceNotFoundException ex) {
            log.info("Expired draft {} left for later: {}", draft.getId(), ex.getMessage());
        }
    }

    private Order completeDraft(PendingCheckout draft) {
        String intentId = draft.getStripePaymentIntentId();
        if (intentId == null) {
            throw BusinessRuleException.conflict("This checkout has no payment yet.");
        }

        PaymentIntent intent = paymentService.retrieveIntent(intentId);
        if (!"succeeded".equals(intent.getStatus())) {
            // Includes "processing": the webhook finishes it when the money lands.
            throw BusinessRuleException.conflict(
                "Your payment hasn't completed yet. If you were charged, your order will appear shortly.");
        }

        long expected = paymentService.toMinorUnits(draft.getAmountDue(), draft.getCurrency());
        if (intent.getAmountReceived() == null || intent.getAmountReceived() != expected) {
            log.error("Draft {} expected {} but intent {} collected {}", draft.getId(), expected, intentId,
                intent.getAmountReceived());
            return refundAndFail(draft.getId(), intentId, "The amount charged didn't match your order.");
        }
        return createOrderOrRefund(draft.getId(), intentId);
    }

    private Order createOrderOrRefund(UUID draftId, String intentId) {
        try {
            return orderService.createOrderFromDraft(draftId, intentId);
        } catch (CheckoutFailedException ex) {
            return refundAndFail(draftId, intentId, ex.getMessage());
        }
    }

    // The customer paid but there is no order to give them: return the money, drop the draft, say why.
    // If the refund itself fails the draft is kept, so the next webhook or expiry run retries it
    // (the idempotency key guarantees one refund); the error is logged loudly for staff.
    private Order refundAndFail(UUID draftId, String intentId, String reason) {
        if (intentId != null) {
            try {
                paymentService.refundPaymentIntent(intentId, "refund-draft-" + draftId);
            } catch (PaymentException ex) {
                log.error("REFUND FAILED for unfulfillable checkout {} (intent {}). Refund it in the Stripe Dashboard.",
                    draftId, intentId, ex);
                throw ex;
            }
        }
        orderService.discardDraft(draftId);
        throw BusinessRuleException.conflict(
            reason + (intentId != null ? " Your payment has been refunded." : ""));
    }
}
