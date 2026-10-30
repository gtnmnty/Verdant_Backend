package com.verdant.salon_ecomm.integration;

import com.verdant.salon_ecomm.AbstractIntegrationTest;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.services.UserService;
import com.verdant.salon_ecomm.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Self-service account deletion (UserService.deleteUserById): personal rows deleted, retained records
// (orders, newsletter, audit) anonymized, the account itself kept but unusable, external cleanup queued.
class AccountDeletionCleanupTest extends AbstractIntegrationTest {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void deletionRemovesPersonalDataAndKeepsBusinessRecords() {
        User user = data.customer();
        String email = user.getEmail();
        UUID id = user.getId();
        user.setStripeCustomerId("cus_test_" + UUID.randomUUID());
        user.setPhone("+63 912 345 6789");
        userRepository.saveAndFlush(user);

        Product product = data.product(5);
        data.cartItem(user, product, 1);
        Order order = data.orderAwaitingPayment(user, "pi_" + UUID.randomUUID());
        jdbc.update("insert into favorites (user_id, target_id, target_type) values (?, ?, 'PRODUCT')", id, product.getId());
        jdbc.update("insert into notifications (user_id, type, title, message) values (?, 'APPOINTMENT_CREATED', 'Hi', 'Hello')", id);
        jdbc.update("insert into refresh_tokens (user_id, token_hash, expires_at) values (?, ?, now() + interval '1 day')",
            id, "hash-" + UUID.randomUUID());
        jdbc.update("insert into password_reset_tokens (user_id, token_hash, expires_at) values (?, ?, now() + interval '1 hour')",
            id, UUID.randomUUID().toString().replace("-", "").substring(0, 32));
        jdbc.update("insert into newsletter_subscribers (id, email, status, unsubscribe_token) values (?, ?, 'SUBSCRIBED', ?)",
            UUID.randomUUID(), email, UUID.randomUUID().toString());
        jdbc.update("insert into audit_logs (entity_type, action_type, title, detail) values ('USER', 'UPDATED', ?, ?)",
            "Profile of " + email + " updated", "by " + email);

        userService.deleteUserById(id);

        // personal rows: gone
        assertThat(count("select count(*) from cart_items where user_id = ?", id)).isZero();
        assertThat(count("select count(*) from favorites where user_id = ?", id)).isZero();
        assertThat(count("select count(*) from notifications where user_id = ?", id)).isZero();
        assertThat(count("select count(*) from refresh_tokens where user_id = ?", id)).isZero();
        assertThat(count("select count(*) from password_reset_tokens where user_id = ?", id)).isZero();

        // the account: kept for history, anonymized, unusable
        Map<String, Object> row = jdbc.queryForMap(
            "select full_name, email, phone, status, enabled, email_verified, stripe_customer_id from users where id = ?", id);
        assertThat(row.get("email")).isEqualTo("deleted-" + id + "@deleted.verdant.local");
        assertThat(row.get("full_name")).isEqualTo("Deleted User");
        assertThat(row.get("phone")).isNull();
        assertThat(row.get("stripe_customer_id")).isNull();
        assertThat(row.get("status")).isEqualTo(AccountStatus.DELETED.name());
        assertThat(row.get("enabled")).isEqualTo(false);
        assertThat(row.get("email_verified")).isEqualTo(false);

        // business records: kept, still linked to the (anonymized) account
        assertThat(count("select count(*) from orders where id = ? and user_id = ?", order.getId(), id)).isEqualTo(1);

        // the original address appears nowhere any more
        assertThat(count("select count(*) from users where lower(email) = lower(?)", email)).isZero();
        assertThat(count("select count(*) from newsletter_subscribers where email = ?", email)).isZero();
        assertThat(count("select count(*) from newsletter_subscribers where email = ? and status = 'UNSUBSCRIBED'",
            "deleted-" + id + "@deleted.verdant.local")).isEqualTo(1);
        assertThat(count("select count(*) from audit_logs where strpos(title, ?) > 0 or strpos(detail, ?) > 0", email, email))
            .isZero();

        // the Stripe customer is removed later by DeletedUserCleanupJob, outside the transaction
        assertThat(count("select count(*) from pending_cleanup_jobs where user_id = ? and stripe_customer_id like 'cus_test_%'", id))
            .isEqualTo(1);
    }

    @Test
    void theAddressIsFreeToRegisterAgain() {
        User user = data.customer();
        String email = user.getEmail();

        userService.deleteUserById(user.getId());

        User again = data.user(user.getRole(), email);
        assertThat(again.getId()).isNotEqualTo(user.getId());
    }

    @Test
    void nothingIsQueuedWhenThereIsNothingExternalToRemove() {
        User user = data.customer();

        userService.deleteUserById(user.getId());

        assertThat(count("select count(*) from pending_cleanup_jobs where user_id = ?", user.getId())).isZero();
    }
}
