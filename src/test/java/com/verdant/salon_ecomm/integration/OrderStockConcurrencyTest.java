package com.verdant.salon_ecomm.integration;

import com.verdant.salon_ecomm.AbstractIntegrationTest;
import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.dtos.order.PlaceOrderInput;
import com.verdant.salon_ecomm.exceptions.CheckoutFailedException;
import com.verdant.salon_ecomm.models.entities.CartItem;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.PendingCheckout;
import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.services.OrderService;
import com.verdant.salon_ecomm.support.Concurrently;
import com.verdant.salon_ecomm.support.Concurrently.Outcome;
import com.verdant.salon_ecomm.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Stock is taken when the order is created from a paid draft, with one conditional UPDATE (stock >= qty), so
// parallel orders can never oversell or drive stock negative; @Version catches lost updates on product edits.
class OrderStockConcurrencyTest extends AbstractIntegrationTest {

    private static final AddressInput ADDRESS =
        new AddressInput("1 Test St", null, "Manila", "NCR", "1000", "PH");

    @Autowired OrderService orderService;
    @Autowired ProductRepository productRepository;
    @Autowired TestData data;
    @Autowired TransactionTemplate tx;

    private static PlaceOrderInput checkout(CartItem... items) {
        return new PlaceOrderInput(
            java.util.Arrays.stream(items).map(CartItem::getId).toList(),
            "Test Buyer", "0917000000", "buyer@test.verdant.local", ADDRESS, "card", false, null);
    }

    // A draft only checks stock, it never holds it, so every buyer gets one; stock is taken at order creation.
    private PendingCheckout draft(User user, CartItem... items) {
        return orderService.createCustomerDraft(user.getId(), checkout(items));
    }

    @Test
    void parallelCheckoutsNeverOversell() throws Exception {
        int stock = 3, buyers = 8;
        Product product = data.product(stock);
        List<User> users = new ArrayList<>();
        List<CartItem> carts = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            User u = data.customer();
            users.add(u);
            carts.add(data.cartItem(u, product, 1));
        }

        List<PendingCheckout> drafts = new ArrayList<>();
        for (int i = 0; i < buyers; i++) drafts.add(draft(users.get(i), carts.get(i)));

        List<Outcome<Order>> outcomes = Concurrently.run(buyers,
            i -> () -> orderService.createOrderFromDraft(drafts.get(i).getId(), "pi_race_" + drafts.get(i).getId()));

        assertThat(Concurrently.successes(outcomes)).isEqualTo(stock);
        assertThat(outcomes).filteredOn(o -> !o.succeeded())
            .allSatisfy(o -> assertThat(o.error()).isInstanceOf(CheckoutFailedException.class));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
    }

    @Test
    void multiUnitOrdersReserveExactlyWhatIsLeft() throws Exception {
        Product product = data.product(5);
        User a = data.customer(), b = data.customer();
        CartItem aCart = data.cartItem(a, product, 3), bCart = data.cartItem(b, product, 3);

        PendingCheckout aDraft = draft(a, aCart), bDraft = draft(b, bCart);

        List<Outcome<Order>> outcomes = Concurrently.run(2,
            i -> () -> orderService.createOrderFromDraft(
                (i == 0 ? aDraft : bDraft).getId(), "pi_multi_" + i + "_" + aDraft.getId()));

        // 3 + 3 > 5: exactly one wins, and the loser's 3 units are not taken.
        assertThat(Concurrently.successes(outcomes)).isEqualTo(1);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(2);
    }

    @Test
    void failedOrderCreationTakesNoStockAndKeepsTheDraft() {
        Product inStock = data.product(10), soonGone = data.product(1);
        User u = data.customer();
        CartItem ok = data.cartItem(u, inStock, 2), gone = data.cartItem(u, soonGone, 1);
        PendingCheckout draft = draft(u, ok, gone);

        // The second item sells out while the customer is typing their card.
        tx.executeWithoutResult(s -> productRepository.decrementStock(soonGone.getId(), 1));

        assertThatThrownBy(() -> orderService.createOrderFromDraft(draft.getId(), "pi_fail_" + draft.getId()))
            .isInstanceOf(CheckoutFailedException.class);

        // Everything rolls back, including the units already taken for the first item.
        assertThat(productRepository.findById(inStock.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(orderService.findDraft(draft.getId())).isPresent();
    }

    @Test
    void staleProductEditIsRejectedByOptimisticLocking() {
        Product product = data.product(10);

        // Two editors load the same version; the first save wins, the second must not overwrite it.
        Product first = productRepository.findById(product.getId()).orElseThrow();
        Product second = productRepository.findById(product.getId()).orElseThrow();
        first.setPrice(new BigDecimal("30.00"));
        tx.executeWithoutResult(s -> productRepository.saveAndFlush(first));
        second.setPrice(new BigDecimal("99.00"));

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> productRepository.saveAndFlush(second)))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getPrice()).isEqualByComparingTo("30.00");
    }

    @Test
    void stockReservationBumpsTheVersionSoAnEditMadeBeforeItIsRejected() {
        Product product = data.product(10);
        Product staleEdit = productRepository.findById(product.getId()).orElseThrow();
        User u = data.customer();

        PendingCheckout draft = draft(u, data.cartItem(u, product, 1));
        orderService.createOrderFromDraft(draft.getId(), "pi_version_" + draft.getId());

        staleEdit.setStockQuantity(10);   // would silently undo the reservation
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> productRepository.saveAndFlush(staleEdit)))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(9);
    }
}
