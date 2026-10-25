package com.verdant.salon_ecomm.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

// M-7: any signed-in customer could call this, and it returned the JDBC URL (DB host, port, database
// name, connection parameters) and, on failure, the raw driver error message.
// Now OWNER only, and the body is just {"status":"UP"} or {"status":"DOWN"}. The failure details go
// to the server log. (The audit's other option, Spring Boot Actuator, would add a dependency; this
// keeps the endpoint without exposing anything.)
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    @GetMapping("/db-test")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Map<String, String>> testDb() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                return ResponseEntity.ok(Map.of("status", "UP"));
            }
            log.warn("DB health check: connection is not valid");
        } catch (Exception e) {
            log.error("DB health check failed", e);
        }
        return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }
}
