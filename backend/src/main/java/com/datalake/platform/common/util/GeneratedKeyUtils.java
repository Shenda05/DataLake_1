package com.datalake.platform.common.util;

import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.support.KeyHolder;

public final class GeneratedKeyUtils {

    private GeneratedKeyUtils() {
    }

    public static Long getLongId(KeyHolder keyHolder, String preferredKey) {
        if (!keyHolder.getKeyList().isEmpty()) {
            Map<String, Object> keys = keyHolder.getKeyList().get(0);
            String lowerPreferred = preferredKey.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Object> entry : keys.entrySet()) {
                if (entry.getKey().toLowerCase(Locale.ROOT).equals(lowerPreferred) && entry.getValue() instanceof Number number) {
                    return number.longValue();
                }
            }
            Object value = keys.values().iterator().next();
            if (value instanceof Number number) {
                return number.longValue();
            }
        }
        throw new IllegalStateException("未获取到数据库自增主键");
    }
}
