package com.verdant.salon_ecomm.dtos.appointment;

import java.util.List;

// Everything the appointment email template shows, already formatted as text (the template only escapes and
// lays it out). details = the label/value rows (reference, service, when, where ...).
public record AppointmentEmailContent(
    String heading,
    String greetingName,
    List<String> paragraphs,
    List<Detail> details,
    String ctaLabel,
    String ctaUrl
) {
    public record Detail(String label, String value) {}
}
