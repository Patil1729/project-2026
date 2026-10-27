package com.party.registration.admin.search.repository;

import java.util.Locale;

/** User-entered %, _ and backslash are literal characters, not SQL LIKE wildcards. */
public final class LiteralSearchPattern {
    private LiteralSearchPattern() { }

    public static String contains(String input) {
        if (input == null || input.isBlank()) return null;
        return "%" + input.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
