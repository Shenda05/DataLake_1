package com.datalake.platform.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SqlNameUtilsWhiteBoxTest {

    @Test
    @DisplayName("白盒测试：表名会被规范化，空表名使用默认值")
    void sanitizeTableNameNormalizesInputAndFallbacksWhenBlank() {
        assertEquals("order_detail_2026_", SqlNameUtils.sanitizeTableName(" Order-Detail 2026! "));
        assertEquals("dl_dataset_tmp", SqlNameUtils.sanitizeTableName("   "));
        assertEquals("dl_dataset_tmp", SqlNameUtils.sanitizeTableName(null));
    }

    @Test
    @DisplayName("白盒测试：字段名数字开头时自动补 c_ 前缀")
    void sanitizeColumnNameAddsPrefixWhenStartsWithDigit() {
        assertEquals("c_2026_sales", SqlNameUtils.sanitizeColumnName("2026 Sales"));
        assertEquals("field_col", SqlNameUtils.sanitizeColumnName(""));
        assertEquals("user_name", SqlNameUtils.sanitizeColumnName("User Name"));
    }
}
