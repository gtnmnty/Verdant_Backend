package com.verdant.salon_ecomm;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// Extend this instead of writing @SpringBootTest directly on simulation/integration
// tests. Spins up a disposable Postgres container per test class (reused across
// test methods in the same class via Testcontainers' container lifecycle), so
// tests never touch the shared Supabase database. @ServiceConnection wires the
// datasource properties automatically, overriding whatever spring.datasource.*
// values come from application-local.properties — no manual property juggling needed.
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("salon_ecomm_test")
        .withUsername("test")
        .withPassword("test");

    // The container starts empty — no Supabase schema to validate against, so
    // let Hibernate create it from the entities for the lifetime of the container.
    // This intentionally overrides application-local.properties' ddl-auto=validate.
    @DynamicPropertySource
    static void overrideDdlAuto(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
    }
}
