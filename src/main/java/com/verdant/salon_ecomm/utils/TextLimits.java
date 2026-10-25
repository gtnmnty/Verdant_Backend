package com.verdant.salon_ecomm.utils;

// M-33: listeners build notification and audit text from user input (appointment notes, stylist bios, review
// text) with no length limit. An over-long value failed the INSERT, the listener swallowed the error, and the
// notification or audit row was silently lost. Truncating at the write keeps the row and marks the cut.
public final class TextLimits {

    private static final String ELLIPSIS = "...";

    private TextLimits() {
    }

    // Returns text unchanged if it fits, otherwise the first (max - 3) characters plus "...".
    // null stays null, so optional columns keep working.
    public static String truncate(String text, int max) {
        if (text == null || text.length() <= max) return text;
        if (max <= ELLIPSIS.length()) return text.substring(0, max);
        return text.substring(0, max - ELLIPSIS.length()) + ELLIPSIS;
    }
}
