package com.verdant.salon_ecomm.utils;

import java.util.regex.Pattern;

// Shared checks for free-text fields on inputs that aren't bean-validated (GraphQL arguments).
public final class InputValidators {

    public static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private InputValidators() {}

    // Trimmed value; throws when it is missing, blank or longer than max.
    public static String requireText(String value, int max, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(label + " must be at most " + max + " characters");
        }
        return trimmed;
    }

    // Like requireText, but a missing or blank value is allowed and comes back as null.
    public static String optionalText(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        return requireText(value, max, label);
    }

    public static boolean isEmail(String value) {
        return value != null && EMAIL.matcher(value).matches();
    }
}
