package com.datalake.platform.common.domain;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BusinessDomainCatalog {

    public static final String DEFAULT_DOMAIN = "TRADE";

    public static final List<String> ALL_DOMAINS = List.of(
        "USER",
        "PRODUCT",
        "TRADE",
        "PAYMENT",
        "INVENTORY",
        "REVIEW",
        "BEHAVIOR_LOG"
    );

    private static final Set<String> DOMAIN_SET = Set.copyOf(ALL_DOMAINS);

    private BusinessDomainCatalog() {
    }

    public static String normalize(String rawDomain) {
        if (rawDomain == null || rawDomain.isBlank()) {
            return DEFAULT_DOMAIN;
        }
        String normalized = rawDomain.trim().toUpperCase(Locale.ROOT);
        if (!DOMAIN_SET.contains(normalized)) {
            throw new IllegalArgumentException("不支持的业务域: " + rawDomain + "，可选值: " + String.join(",", ALL_DOMAINS));
        }
        return normalized;
    }

    public static boolean isValid(String rawDomain) {
        if (rawDomain == null || rawDomain.isBlank()) {
            return false;
        }
        return DOMAIN_SET.contains(rawDomain.trim().toUpperCase(Locale.ROOT));
    }
}
