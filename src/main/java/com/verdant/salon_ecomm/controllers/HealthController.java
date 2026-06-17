package com.verdant.salon_ecomm.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;

@RestController
@RequestMapping("/api")
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @GetMapping("/db-test")
    public ResponseEntity<String> testDb() {
        try (Connection conn = dataSource.getConnection()) {
            return ResponseEntity.ok("✅ DB connected: " + conn.getMetaData().getURL());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ DB connection failed: " + e.getMessage());
        }
    }
}