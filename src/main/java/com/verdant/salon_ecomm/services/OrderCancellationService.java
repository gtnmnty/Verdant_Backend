package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.verdant.salon_ecomm.exceptions.PaymentException;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.enums.orders.OrderCancelReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

// Customer cancel = DB changes in one transaction (OrderService), then the card refund at Stripe once that
// has committed. Deliberately not @Transactional itself: the Stripe call must not run inside, or hold, the
// order's row lock. If the refund fails the order stays cancelled (stock and wallet are already back) and
// the customer is told; staff can refund it from the Stripe Dashboard.
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCancellationService {

    private final OrderService orderService;
    private final PaymentService paymentService;

    public Order cancelMyOrder(UUID userId, UUID orderId, OrderCancelReason reason, String note) {
        OrderService.CustomerCancelResult result = orderService.cancelOrderForCustomer(userId, orderId, reason, note);

        if (result.cardRefundAmount() != null) {
            try {
                paymentService.refundOrder(orderId, result.paymentIntentId(), result.cardRefundAmount());
            } catch (PaymentException ex) {
                log.error("REFUND FAILED for cancelled order {} (intent {}, amount {}). Refund it manually in the Stripe Dashboard.",
                    result.order().getOrderCode(), result.paymentIntentId(), result.cardRefundAmount(), ex);
                // BusinessRuleException, not PaymentException: the latter is shown to the client as a generic
                // "payment could not be processed", which would hide that the order itself was cancelled.
                throw BusinessRuleException.conflict(
                    "Your order was cancelled, but the refund to your card could not be processed automatically. "
                        + "Our team has been notified and will refund you.");
            }
        }
        return result.order();
    }
}
