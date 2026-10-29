package com.verdant.salon_ecomm;

import com.cloudinary.Cloudinary;
import com.stripe.Stripe;
import com.stripe.StripeClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;

// Guards the test setup itself (AbstractIntegrationTest): if any of these fail, the suite could reach a real
// database, payment account, image store or inbox. Fix the setup; never relax these assertions.
class ExternalServicesSafetyTest extends AbstractIntegrationTest {

    @Autowired DataSource dataSource;
    @Autowired StripeClient stripeClientBean;
    @Autowired Cloudinary cloudinaryBean;
    @Autowired JavaMailSender mailSenderBean;

    @Value("${stripe.api-key}") String stripeKey;
    @Value("${stripe.webhook-secret}") String webhookSecret;
    @Value("${cloudinary.api-secret}") String cloudinarySecret;
    @Value("${spring.mail.host}") String mailHost;

    @Test
    void databaseIsTheTestcontainer() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            String url = c.getMetaData().getURL();
            assertThat(url).isEqualTo(POSTGRES.getJdbcUrl());
            assertThat(url).doesNotContainIgnoringCase("supabase").doesNotContain("pooler");
        }
    }

    @Test
    void externalClientsAreMocks() {
        assertThat(mockingDetails(stripeClientBean).isMock()).as("StripeClient is a mock").isTrue();
        assertThat(mockingDetails(cloudinaryBean).isMock()).as("Cloudinary is a mock").isTrue();
        assertThat(mockingDetails(mailSenderBean).isMock()).as("JavaMailSender is a mock").isTrue();
    }

    @Test
    void credentialsAreDummies() {
        assertThat(stripeKey).isEqualTo("sk_test_not_a_real_key");
        assertThat(Stripe.apiKey).isEqualTo("sk_test_not_a_real_key");
        assertThat(webhookSecret).isEqualTo("whsec_not_a_real_secret");
        assertThat(cloudinarySecret).isEqualTo("test-cloudinary-secret");
        assertThat(mailHost).isEqualTo("localhost");
    }

    @Test
    void stripeStaticApiPointsNowhere() {
        assertThat(Stripe.getApiBase()).isEqualTo(DEAD_STRIPE_API_BASE);
    }
}
