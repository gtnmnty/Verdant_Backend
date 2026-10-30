package com.verdant.salon_ecomm;

import com.cloudinary.Cloudinary;
import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.verdant.salon_ecomm.support.TestData;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Answers;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.mockito.Mockito.when;

// Extend this instead of writing @SpringBootTest directly on integration tests.
//
// - Postgres and Redis run in throwaway containers, started once per test JVM and shared by every test class
//   (Ryuk removes them at exit). Tests never touch the Supabase/production database.
// - The schema comes from the Flyway migrations (V1 baseline onwards) and Hibernate only validates it
//   (ddl-auto=validate), so tests run against the same schema as production. See application-test.properties.
// - Nothing leaves the machine. The three beans every external call goes through are Mockito mocks:
//   StripeClient (PaymentService, GiftCardStripeGateway), Cloudinary (under CloudinaryService) and
//   JavaMailSender (under EmailService). The app code above them runs for real; a test stubs what it needs,
//   e.g. when(stripeClient.paymentIntents()...) or verify(mailSender).send(any(MimeMessage.class)).
//   Mocks are reset after every test. Webhook.constructEvent only checks a signature locally, so a webhook
//   test signs its payload with the fake secret below.
// - Backstops in case a mock is forgotten or code calls Stripe's static API: every credential is a dummy
//   (backend/.env is imported by application.properties and may hold real keys; DynamicPropertySource beats
//   it), and Stripe's API base points at a closed local port. ExternalServicesSafetyTest checks all of this.
// - Tests that share a context share the database. Use unique data (emails, SKUs) or clean up after yourself.
@SpringBootTest
@ActiveProfiles("test")
@Import(TestData.class)
public abstract class AbstractIntegrationTest {

    // Port 9 (discard) is closed on any normal machine: a real Stripe call fails fast instead of going out.
    static final String DEAD_STRIPE_API_BASE = "http://localhost:9";

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("verdant_test")
        .withUsername("test")
        .withPassword("test");

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
        Stripe.overrideApiBase(DEAD_STRIPE_API_BASE);
        Stripe.overrideUploadBase(DEAD_STRIPE_API_BASE);
        Stripe.overrideConnectBase(DEAD_STRIPE_API_BASE);
    }

    // Deep stubs, so code that only chains through (stripeClient.paymentIntents().create(...)) gets a mock
    // back instead of a NullPointerException; stub the final call when the test cares about the result.
    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    protected StripeClient stripeClient;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    protected Cloudinary cloudinary;

    @MockitoBean
    protected JavaMailSender mailSender;

    // EmailService builds a real MimeMessage (templates, headers) before send(); the mock only skips the SMTP
    // hop. Re-stubbed before every test because mocks are reset after each one.
    @BeforeEach
    void stubMailSender() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
    }

    @DynamicPropertySource
    static void containerAndFakeCredentials(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("security.jwt.secret-key", () -> "dGVzdC1vbmx5LWp3dC1zZWNyZXQtdGVzdC1vbmx5LWp3dC1zZWNyZXQ=");
        registry.add("security.jwt.expiration-time", () -> "3600000");
        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> "test-google-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-google-secret");
        registry.add("stripe.api-key", () -> "sk_test_not_a_real_key");
        registry.add("stripe.webhook-secret", () -> "whsec_not_a_real_secret");
        registry.add("cloudinary.cloud-name", () -> "test-cloud");
        registry.add("cloudinary.api-key", () -> "000000000000000");
        registry.add("cloudinary.api-secret", () -> "test-cloudinary-secret");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "3025");
        registry.add("spring.mail.username", () -> "test@example.invalid");
        registry.add("spring.mail.password", () -> "test");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:3000");
    }
}
