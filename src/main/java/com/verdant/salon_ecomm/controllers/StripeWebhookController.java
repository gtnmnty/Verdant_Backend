package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.services.PaymentService;
import lombok.RequiredArgsConstructor;
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

        // InvalidWebhookSignatureException is intentionally NOT caught here -
        // it propagates to GlobalExceptionHandler.handleInvalidWebhookSignature,
        // so callers get the same standardized ErrorResponse shape as every
        // other endpoint instead of this controller's own raw string body.
        paymentService.handleStripeWebhook(rawPayload, signatureHeader);
        return ResponseEntity.ok("ok");
    }
}