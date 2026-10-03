package com.verdant.salon_ecomm.dtos.gitftcards.events;

import com.verdant.salon_ecomm.models.entities.Order;

/** An order's card payment failed/was cancelled - any gift-card wallet money applied to it must be handed back. */
public record OrderPaymentVoidedEvent(Order order) {}
