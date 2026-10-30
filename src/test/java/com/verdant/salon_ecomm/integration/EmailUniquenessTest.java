package com.verdant.salon_ecomm.integration;

import com.verdant.salon_ecomm.AbstractIntegrationTest;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.support.Concurrently;
import com.verdant.salon_ecomm.support.Concurrently.Outcome;
import com.verdant.salon_ecomm.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// M-36: an email is one account regardless of case or surrounding spaces, enforced by the entity
// (EmailNormalizer on write), the lookups (lower(email) = ?) and the database (uq_users_email_lower, V12).
class EmailUniquenessTest extends AbstractIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;

    private static String mixedCase() {
        return "  Jane." + UUID.randomUUID().toString().substring(0, 8) + "@Example.COM ";
    }

    @Test
    void emailIsStoredTrimmedAndLowerCase() {
        String typed = mixedCase();
        User user = data.user(AccountRole.CUSTOMER, typed);

        String stored = jdbc.queryForObject("select email from users where id = ?", String.class, user.getId());
        assertThat(stored).isEqualTo(typed.trim().toLowerCase());
    }

    @Test
    void lookupIgnoresCaseAndSpaces() {
        String typed = mixedCase();
        User user = data.user(AccountRole.CUSTOMER, typed);

        assertThat(userRepository.findByEmail(typed.trim().toUpperCase())).map(User::getId).contains(user.getId());
        assertThat(userRepository.existsByEmail(" " + typed.trim().toLowerCase() + " ")).isTrue();
    }

    @Test
    void sameAddressInAnotherCaseIsRejected() {
        String typed = mixedCase();
        data.user(AccountRole.CUSTOMER, typed);

        assertThatThrownBy(() -> data.user(AccountRole.CUSTOMER, typed.trim().toUpperCase()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsACaseVariantEvenIfTheAppIsBypassed() {
        String email = "raw-" + UUID.randomUUID() + "@x.com";
        data.user(AccountRole.CUSTOMER, email);

        assertThatThrownBy(() -> jdbc.update(
            "insert into users (full_name, email, password_hash) values ('Raw', ?, 'x')", email.toUpperCase()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void parallelSignupsWithTheSameAddressCreateOneAccount() throws Exception {
        String base = "race-" + UUID.randomUUID() + "@x.com";

        List<Outcome<User>> outcomes = Concurrently.run(5, i -> () -> data.user(AccountRole.CUSTOMER,
            i % 2 == 0 ? base.toUpperCase() : " " + base));

        assertThat(Concurrently.successes(outcomes)).isEqualTo(1);
        assertThat(outcomes).filteredOn(o -> !o.succeeded())
            .allSatisfy(o -> assertThat(o.error()).isInstanceOf(DataIntegrityViolationException.class));
        assertThat(jdbc.queryForObject("select count(*) from users where lower(email) = ?", Integer.class, base))
            .isEqualTo(1);
    }
}
