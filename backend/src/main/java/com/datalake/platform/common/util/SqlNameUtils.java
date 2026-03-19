package com.datalake.platform.common.util;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class SqlNameUtils {

    private SqlNameUtils() {
    }

    public static String sanitizeTableName(String input) {
        return sanitizeIdentifier(input, "t_");
    }

    public static String sanitizeColumnName(String input) {
        return sanitizeIdentifier(input, "c_");
    }

    public static String ensureUnique(String candidate, Set<String> existing) {
        if (!existing.contains(candidate)) {
            existing.add(candidate);
            return candidate;
        }
        int suffix = 2;
        String next = candidate + "_" + suffix;
        while (existing.contains(next)) {
            suffix += 1;
            next = candidate + "_" + suffix;
        }
        existing.add(next);
        return next;
    }

    public static Set<String> newNameSet() {
        return new HashSet<>();
    }

    private static String sanitizeIdentifier(String input, String prefix) {
        String sanitized = input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        if (sanitized.isBlank()) {
            sanitized = prefix + "field";
        }
        if (!Character.isLetter(sanitized.charAt(0))) {
            sanitized = prefix + sanitized;
        }
        return sanitized;
    }
}

