package com.verdant.salon_ecomm.services.events.listeners.order;

import com.verdant.salon_ecomm.config.StripeConfig;
import com.verdant.salon_ecomm.dtos.order.OrderEmailContent;
import com.verdant.salon_ecomm.dtos.order.OrderEmailContent.Line;
import com.verdant.salon_ecomm.dtos.order.events.OrderCancelledByCustomerEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderCreatedByAdminEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderPlacedEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderUpdatedEvent;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.OrderItem;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import com.verdant.salon_ecomm.repositories.OrderItemRepository;
import com.verdant.salon_ecomm.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Emails the customer about their order: confirmation, status changes, cancellation and refunds. Same rules as
// GiftCardEmailListener: runs after the DB commit and off the request/webhook thread (@Async), so a slow or failing
// mail server can never delay a Stripe webhook or roll back an order. Failures are logged with the order id only
// (never the address). Only plain Order columns are read here: the session is closed by the time this runs, so
// lazy relations (user, items' product) must not be touched.
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEmailListener {

    private final EmailService emailService;
    private final OrderItemRepository orderItemRepository;
    private final StripeConfig stripeConfig;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        sendConfirmation(event.order(), event.customer().getEmail());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreatedByAdmin(OrderCreatedByAdminEvent event) {
        sendConfirmation(event.order(), null);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderUpdated(OrderUpdatedEvent event) {
        Order order = event.order();

        if (event.orderStatusChanged()) {
            OrderStatus status = order.getOrderStatus();
            switch (status) {
                case CANCELLED -> sendCancellation(order, false, BigDecimal.ZERO, null);
                case PROCESSING -> sendStatusUpdate(order, "We're preparing your order",
                    "Good news: your order is now being prepared.");
                case IN_TRANSIT -> sendStatusUpdate(order, "Your order is on its way",
                    "Your order has shipped and is on its way to you.");
                case DELIVERED -> sendStatusUpdate(order, "Your order was delivered",
                    "Your order has been delivered. We hope you love it.");
                case PLACED -> sendStatusUpdate(order, "Your order was updated",
                    "Your order is back to placed and will be processed shortly.");
            }
            return;
        }

        if (event.paymentStatusChanged()) {
            PaymentStatus payment = order.getPaymentStatus();
            if (payment == PaymentStatus.REFUNDED || payment == PaymentStatus.PARTIALLY_REFUNDED) {
                String intro = payment == PaymentStatus.REFUNDED
                    ? "Your payment for this order has been refunded in full."
                    : "Part of your payment for this order has been refunded.";
                send(order, null, "Refund issued for " + order.getOrderCode(), new OrderEmailContent(
                    "Your refund has been issued", order.getContactName(),
                    List.of(intro, "Refunds usually reach your card within 5 to 10 business days, depending on your bank."),
                    order.getOrderCode(), null, List.of(), List.of(new Line("Order total", money(order.getTotal()))),
                    "View order", emailService.orderUrl(order.getId())));
            }
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCancelledByCustomer(OrderCancelledByCustomerEvent event) {
        sendCancellation(event.order(), true, event.refundAmount(), event.customer().getEmail());
    }

    // ── Builders ─────────────────────────────────────────

    private void sendConfirmation(Order order, String fallbackEmail) {
        try {
            List<Line> lines = new ArrayList<>();
            for (OrderItem item : orderItemRepository.findByOrder_Id(order.getId())) {
                lines.add(new Line(item.getProductName() + " × " + item.getQuantity(), money(item.getSubtotal())));
            }

            List<Line> summary = new ArrayList<>();
            summary.add(new Line("Subtotal", money(order.getSubtotal())));
            summary.add(new Line("Shipping", order.getDeliveryFee().signum() == 0
                ? "Complimentary" : money(order.getDeliveryFee())));
            if (order.getPromoDiscountAmount() != null && order.getPromoDiscountAmount().signum() > 0) {
                summary.add(new Line("Promo discount", "−" + money(order.getPromoDiscountAmount())));
            }
            summary.add(new Line("Total", money(order.getTotal())));
            BigDecimal wallet = order.getWalletAmountApplied() != null ? order.getWalletAmountApplied() : BigDecimal.ZERO;
            if (wallet.signum() > 0) {
                summary.add(new Line("Paid with gift card", "−" + money(wallet)));
                summary.add(new Line("Paid by card", money(order.getTotal().subtract(wallet))));
            }

            send(order, fallbackEmail, "Order confirmed: " + order.getOrderCode(), new OrderEmailContent(
                "Thank you for your order", order.getContactName(),
                List.of("We've received your payment and your order is confirmed. "
                    + "We'll email you again when it's on its way."),
                order.getOrderCode(), order.getTrackingNumber(), lines, summary,
                "Track your order", emailService.trackingUrl(order.getId())));
        } catch (Exception e) {
            log.error("Failed to build the confirmation email for order {}", order.getId(), e);
        }
    }

    private void sendStatusUpdate(Order order, String heading, String message) {
        send(order, null, heading + ": " + order.getOrderCode(), new OrderEmailContent(
            heading, order.getContactName(), List.of(message),
            order.getOrderCode(), order.getTrackingNumber(), List.of(), List.of(),
            "Track your order", emailService.trackingUrl(order.getId())));
    }

    // byCustomer switches the wording. refundAmount is what goes back to the card (zero: nothing was charged to one).
    private void sendCancellation(Order order, boolean byCustomer, BigDecimal refundAmount, String fallbackEmail) {
        List<String> paragraphs = new ArrayList<>();
        paragraphs.add(byCustomer
            ? "Your order has been cancelled as you asked."
            : "Your order has been cancelled by our team. If you didn't expect this, please contact us.");
        if (order.getCancelReason() != null && !order.getCancelReason().isBlank()) {
            paragraphs.add("Reason: " + order.getCancelReason());
        }
        if (refundAmount.signum() > 0) {
            paragraphs.add(money(refundAmount) + " is being refunded to your card. It usually arrives within 5 to 10 "
                + "business days, depending on your bank.");
        }
        BigDecimal walletPortion = order.getTotal().subtract(refundAmount);
        if (byCustomer && walletPortion.signum() > 0) {
            paragraphs.add(money(walletPortion) + " paid with your gift card balance has been returned to your wallet.");
        }

        send(order, fallbackEmail, "Order cancelled: " + order.getOrderCode(), new OrderEmailContent(
            "Your order was cancelled", order.getContactName(), paragraphs,
            order.getOrderCode(), null, List.of(), List.of(), "View order", emailService.orderUrl(order.getId())));
    }

    // ── Helpers ──────────────────────────────────────────

    private void send(Order order, String fallbackEmail, String subject, OrderEmailContent content) {
        // The order's own contact email is what the customer typed at checkout; the account email is the fallback.
        String to = order.getContactEmail() != null && !order.getContactEmail().isBlank()
            ? order.getContactEmail() : fallbackEmail;
        if (to == null || to.isBlank()) return;
        try {
            emailService.sendOrderEmail(to, subject, content);
        } catch (Exception e) {
            log.error("Failed to email order {} ({})", order.getId(), subject, e);
        }
    }

    private String money(BigDecimal amount) {
        return stripeConfig.getCurrency().toUpperCase(Locale.ROOT) + " " + amount.setScale(2, RoundingMode.HALF_UP);
    }
}
