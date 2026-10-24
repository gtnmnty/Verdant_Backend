package com.verdant.salon_ecomm.specifications;

import java.util.Locale;

// Builds case-insensitive "contains" patterns for cb.like(...). The search text is escaped,
// so a user typing "50%" or "a_b" matches those characters literally instead of as wildcards.
// Always pass ESCAPE as the like() escape character together with a pattern from here.
public final class LikePatterns {

    public static final char ESCAPE = '!';

    // Longer input adds nothing useful to a substring search and only makes the scan slower.
    public static final int MAX_SEARCH_LENGTH = 100;

    private LikePatterns() {}

    public static String contains(String search) {
        String trimmed = search.trim();
        if (trimmed.length() > MAX_SEARCH_LENGTH) {
            trimmed = trimmed.substring(0, MAX_SEARCH_LENGTH);
        }
        String escaped = trimmed.toLowerCase(Locale.ROOT)
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
