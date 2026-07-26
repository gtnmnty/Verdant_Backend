package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.stripe.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {
    boolean existsByStripeEventId(String stripeEventId);
}