package com.verdant.salon_ecomm.controllers;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// M-6: local only. dev is a deployed, production-like profile (Secure cookie), so test endpoints
// don't belong there; any other profile answers 404.
@Profile("local")
@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/public")
    public String publicEndpoint() {
        return "This is public";
    }

    @GetMapping("/protected")
    public String protectedEndpoint() {
        return "You are authenticated";
    }
}