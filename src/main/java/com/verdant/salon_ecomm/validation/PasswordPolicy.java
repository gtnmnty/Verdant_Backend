package com.verdant.salon_ecomm.validation;

// The single password rule for every flow that sets a password: signup, change password,
// code-based reset and the staff-issued reset link. @ValidPassword enforces it on DTOs;
// isValid() covers the paths that receive a bare String (e.g. GraphQL arguments).
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final String REGEX = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}$";
    public static final String MESSAGE =
        "Password must be at least 8 characters and contain an uppercase letter, a lowercase letter, and a number";

    private PasswordPolicy() {}

    public static boolean isValid(String password) {
        return password != null && password.matches(REGEX);
    }
}
