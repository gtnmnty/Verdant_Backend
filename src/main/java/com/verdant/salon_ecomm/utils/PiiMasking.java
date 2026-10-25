package com.verdant.salon_ecomm.utils;

// M-22: masks personal data before it goes into audit entries and staff notifications. Those are kept for
// a long time and read by many staff, so they name a person by masked email or phone, never the full value.
// Enough is left to tell two people apart ("j***@gmail.com", "***4567"); the full value stays on the
// account, which only roles allowed to see it can open.
public final class PiiMasking {

    private PiiMasking() {
    }

    // "jane.doe@gmail.com" -> "j***@gmail.com". Keeps the domain (useful for support, rarely identifying).
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "none";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    // "+1 555 123 4567" -> "***4567". Only the last 4 digits, the part people use to recognize a number.
    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return "none";
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) return "***";
        return "***" + digits.substring(digits.length() - 4);
    }
}
