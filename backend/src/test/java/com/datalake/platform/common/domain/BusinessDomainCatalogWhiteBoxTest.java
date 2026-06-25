package com.datalake.platform.common.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessDomainCatalogWhiteBoxTest {

    @Test
    @DisplayName("白盒测试：空业务域默认归一化为 TRADE")
    void normalizeBlankDomainReturnsDefaultDomain() {
        assertEquals("TRADE", BusinessDomainCatalog.normalize(null));
        assertEquals("TRADE", BusinessDomainCatalog.normalize("   "));
    }

    @Test
    @DisplayName("白盒测试：业务域会忽略大小写和前后空格")
    void normalizeAcceptsLowerCaseAndWhitespace() {
        assertEquals("INVENTORY", BusinessDomainCatalog.normalize(" inventory "));
        assertEquals("BEHAVIOR_LOG", BusinessDomainCatalog.normalize("behavior_log"));
    }

    @Test
    @DisplayName("白盒测试：非法业务域会被拒绝")
    void normalizeRejectsUnsupportedDomain() {
        assertThrows(IllegalArgumentException.class, () -> BusinessDomainCatalog.normalize("UNKNOWN"));
    }

    @Test
    @DisplayName("白盒测试：isValid 只判断明确有效的业务域")
    void isValidReturnsFalseForBlankAndUnsupportedDomain() {
        assertTrue(BusinessDomainCatalog.isValid("product"));
        assertFalse(BusinessDomainCatalog.isValid(""));
        assertFalse(BusinessDomainCatalog.isValid("UNKNOWN"));
    }
}
