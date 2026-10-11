package com.verdant.salon_ecomm.dtos.newsletter;

//Deliberately identical whether the address is new, already subscribed or previously unsubscribed,
// so the public form can't be used to find out who is on the list.
public record NewsletterSubscribeResultDto(boolean success, String message) {}
