package com.verdant.salon_ecomm.utils;

import java.util.Locale;

// M-36: the one definition of "the same email address": trimmed and lower-cased (Locale.ROOT, so a Turkish
// server locale can't turn "I" into a dotless i). Used on every write (User.setEmail and the entity's
// persist/update callbacks) and every lookup (UserRepository's email methods).
// Before, signup and the admin create path stored the address as typed, login and existsByEmail matched it
// exactly, and UserService lower-cased it. So "Jane@x.com" and "jane@x.com" could both register, and some
// users had to type their email in the exact original case to sign in.
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
