package com.verdant.salon_ecomm.dtos.order;

import java.util.List;

// Everything the order email template shows, already formatted as text (the template only escapes and lays out).
// lines = the ordered items, summary = the totals block (subtotal, shipping, discount, total ...).
public record OrderEmailContent(
    String heading,
    String greetingName,
    List<String> paragraphs,
    String orderCode,
    String trackingNumber,
    List<Line> lines,
    List<Line> summary,
    String ctaLabel,
    String ctaUrl
) {
    public record Line(String label, String value) {}
}
