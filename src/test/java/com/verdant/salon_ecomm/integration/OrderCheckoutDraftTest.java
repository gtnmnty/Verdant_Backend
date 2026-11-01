package com.verdant.salon_ecomm.integration;

import com.verdant.salon_ecomm.AbstractIntegrationTest;
import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.dtos.order.PlaceOrderInput;
import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.verdant.salon_ecomm.models.entities.CartItem;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.PendingCheckout;
import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.models.enums.orders.OrderCancelReason;
import com.verdant.salon_ecomm.models.enums.orders.OrderCancelledBy;
import com.verdant.salon_ecomm.models.enums.orders.OrderStatus;
import com.verdant.salon_ecomm.repositories.CartItemRepository;
import com.verdant.salon_ecomm.repositories.OrderItemRepository;
import com.verdant.salon_ecomm.repositories.OrderRepository;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.services.OrderService;
import com.verdant.salon_ecomm.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// An order row is written only once it is paid: the draft holds the checkout, createOrderFromDraft turns it
// into a PAID order, and customer cancel works on that paid order.
class OrderCheckoutDraftTest extends AbstractIntegrationTest {

    private static final AddressInput ADDRESS =
        new AddressInput("1 Test St", null, "Manila", "NCR", "1000", "PH");

    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired TestData data;

    private PendingCheckout draft(User user, CartItem... items) {
        return orderService.createCustomerDraft(user.getId(), new PlaceOrderInput(
            java.util.Arrays.stream(items).map(CartItem::getId).toList(),
            "Test Buyer", "0917000000", "buyer@test.verdant.local", ADDRESS, "card", false, null));
    }

    private Order paidOrder(User user, Product product, int quantity) {
        PendingCheckout draft = draft(user, data.cartItem(user, product, quantity));
        return orderService.createOrderFromDraft(draft.getId(), "pi_test_" + UUID.randomUUID());
    }

    @Test
    void draftCreatesNoOrderAndHoldsNoStock() {
        Product product = data.product(5);
        User u = data.customer();

        PendingCheckout draft = draft(u, data.cartItem(u, product, 2));

        assertThat(orderRepository.count(
            (root, q, cb) -> cb.equal(root.get("user").get("id"), u.getId()))).isZero();
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
        assertThat(draft.getAmountDue()).isEqualByComparingTo(draft.getTotal());
    }

    @Test
    void paidOrderHasTrackingNumberContactPaidDateAndTheDraftsPrice() {
        Product product = data.product(5);
        User u = data.customer();
        PendingCheckout draft = draft(u, data.cartItem(u, product, 2));

        // The shop raises the price while the customer is paying: they are still charged the draft's price.
        product.setPrice(new BigDecimal("99.00"));
        productRepository.saveAndFlush(product);

        Order order = orderService.createOrderFromDraft(draft.getId(), "pi_price_" + draft.getId());

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(order.getTrackingNumber()).matches("VT-[A-HJ-NP-Z2-9]{8}");
        assertThat(order.getContactName()).isEqualTo("Test Buyer");
        assertThat(order.getContactPhone()).isEqualTo("0917000000");
        assertThat(order.getContactEmail()).isEqualTo("buyer@test.verdant.local");
        assertThat(orderItemRepository.findByOrder_Id(order.getId()))
            .singleElement()
            .satisfies(item -> assertThat(item.getUnitPrice()).isEqualByComparingTo("25.00"));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(3);
        assertThat(orderService.findDraft(draft.getId())).isEmpty();
    }

    @Test
    void creatingTheSameDraftTwiceReturnsTheSameOrder() {
        Product product = data.product(5);
        User u = data.customer();
        PendingCheckout draft = draft(u, data.cartItem(u, product, 1));
        String intent = "pi_twice_" + draft.getId();

        // Browser finalize and the webhook both fire: one order, stock taken once.
        Order first = orderService.createOrderFromDraft(draft.getId(), intent);
        Order second = orderService.createOrderFromDraft(draft.getId(), intent);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(4);
    }

    @Test
    void onlyThePaidQuantityLeavesTheCart() {
        Product product = data.product(10);
        User u = data.customer();
        CartItem cartRow = data.cartItem(u, product, 3);
        PendingCheckout draft = draft(u, cartRow);

        // Buy Again in another tab adds 2 more of the same item while the customer is paying.
        cartRow.setQuantity(5);
        cartItemRepository.saveAndFlush(cartRow);

        orderService.createOrderFromDraft(draft.getId(), "pi_cart_" + draft.getId());

        assertThat(cartItemRepository.findById(cartRow.getId()).orElseThrow().getQuantity()).isEqualTo(2);
    }

    @Test
    void fullyPaidCartRowIsRemoved() {
        Product product = data.product(10);
        User u = data.customer();
        CartItem cartRow = data.cartItem(u, product, 3);
        PendingCheckout draft = draft(u, cartRow);

        orderService.createOrderFromDraft(draft.getId(), "pi_cartgone_" + draft.getId());

        assertThat(cartItemRepository.findById(cartRow.getId())).isEmpty();
    }

    @Test
    void trackingNumbersAreUnique() {
        Product product = data.product(50);
        User u = data.customer();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 15; i++) {
            seen.add(paidOrder(u, product, 1).getTrackingNumber());
        }
        assertThat(seen).hasSize(15);
    }

    // ---------- Customer cancel ----------

    @Test
    void customerCancelRestoresStockAndAsksForTheCardRefund() {
        Product product = data.product(5);
        User u = data.customer();
        Order order = paidOrder(u, product, 2);

        OrderService.CustomerCancelResult result =
            orderService.cancelOrderForCustomer(u.getId(), order.getId(), OrderCancelReason.CHANGED_MIND, null);

        assertThat(result.order().getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.order().getCancelledBy()).isEqualTo(OrderCancelledBy.CUSTOMER);
        assertThat(result.order().getCancelReason()).isEqualTo("Changed my mind");
        assertThat(result.paymentIntentId()).isEqualTo(order.getStripePaymentIntentId());
        assertThat(result.cardRefundAmount()).isEqualByComparingTo(order.getTotal());
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @Test
    void cancellingTwiceIsRejectedSoTheCardIsRefundedOnce() {
        User u = data.customer();
        Order order = paidOrder(u, data.product(5), 1);
        orderService.cancelOrderForCustomer(u.getId(), order.getId(), OrderCancelReason.CHANGED_MIND, null);

        assertThatThrownBy(() ->
            orderService.cancelOrderForCustomer(u.getId(), order.getId(), OrderCancelReason.CHANGED_MIND, null))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void onlyTheOwnerMayCancel() {
        User owner = data.customer(), stranger = data.customer();
        Order order = paidOrder(owner, data.product(5), 1);

        assertThatThrownBy(() ->
            orderService.cancelOrderForCustomer(stranger.getId(), order.getId(), OrderCancelReason.CHANGED_MIND, null))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shippedOrdersCannotBeCancelled() {
        User u = data.customer();
        Order order = paidOrder(u, data.product(5), 1);
        order.setOrderStatus(OrderStatus.IN_TRANSIT);
        orderRepository.saveAndFlush(order);

        assertThatThrownBy(() ->
            orderService.cancelOrderForCustomer(u.getId(), order.getId(), OrderCancelReason.CHANGED_MIND, null))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void otherReasonKeepsTheFreeTextNote() {
        User u = data.customer();
        Order order = paidOrder(u, data.product(5), 1);

        OrderService.CustomerCancelResult result = orderService.cancelOrderForCustomer(
            u.getId(), order.getId(), OrderCancelReason.OTHER, "  Ordered the wrong shade ");

        assertThat(result.order().getCancelReason()).isEqualTo("Other: Ordered the wrong shade");
    }

    @Test
    void unpaidOrdersAreNotCancellableByTheCustomer() {
        User u = data.customer();
        Order unpaid = data.orderAwaitingPayment(u, "pi_unpaid_" + UUID.randomUUID());

        assertThatThrownBy(() ->
            orderService.cancelOrderForCustomer(u.getId(), unpaid.getId(), OrderCancelReason.CHANGED_MIND, null))
            .isInstanceOf(BusinessRuleException.class);
        assertThat(OrderService.isCancellableByCustomer(unpaid)).isFalse();
    }
}
