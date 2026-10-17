package com.verdant.salon_ecomm.controllers;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"local", "dev"})   // Only active in these profiles, 404 in docker/prod
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