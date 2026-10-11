package com.verdant.salon_ecomm.dtos.newsletter;

// Published after a NEW or RE-subscription commits; NewsletterEmailListener sends the welcome email.
public record NewsletterSubscribedEvent(String email, String unsubscribeToken) {}
