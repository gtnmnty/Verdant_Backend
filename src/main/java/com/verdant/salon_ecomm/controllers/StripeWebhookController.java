package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.exceptions.InvalidWebhookSignatureException;
import com.verdant.salon_ecomm.services.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<String> handleWebhook(
        @RequestBody String rawPayload,
        @RequestHeader("Stripe-Signature") String signatureHeader) {

        try {
            paymentService.handleStripeWebhook(rawPayload, signatureHeader);
            return ResponseEntity.ok("ok");
        } catch (InvalidWebhookSignatureException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signature");
        }
    }
}

