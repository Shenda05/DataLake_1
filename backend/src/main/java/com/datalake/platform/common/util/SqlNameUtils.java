package com.datalake.platform.common.util;

import java.util.Locale;

public final class SqlNameUtils {

    private SqlNameUtils() {
    }

    public static String sanitizeTableName(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? "dl_dataset_tmp" : normalized;
    }

    public static String sanitizeColumnName(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return "field_col";
        }
        if (Character.isDigit(normalized.charAt(0))) {
            return "c_" + normalized;
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
    }
}
