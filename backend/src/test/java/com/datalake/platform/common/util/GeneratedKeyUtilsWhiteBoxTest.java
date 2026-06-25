package com.datalake.platform.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.KeyHolder;

class GeneratedKeyUtilsWhiteBoxTest {

    @Test
    @DisplayName("白盒测试：优先按指定主键名读取自增 ID")
    void getLongIdReadsPreferredKeyIgnoringCase() {
        KeyHolder keyHolder = mock(KeyHolder.class);
        when(keyHolder.getKeyList()).thenReturn(List.of(Map.of("DATASET_ID", 88L, "other_id", 99L)));

        assertEquals(88L, GeneratedKeyUtils.getLongId(keyHolder, "dataset_id"));
    }

    @Test
    @DisplayName("白盒测试：没有指定主键时读取第一个数值键")
    void getLongIdFallsBackToFirstNumericValue() {
        KeyHolder keyHolder = mock(KeyHolder.class);
        when(keyHolder.getKeyList()).thenReturn(List.of(Map.of("generated_key", 123)));

        assertEquals(123L, GeneratedKeyUtils.getLongId(keyHolder, "id"));
    }

    @Test
    @DisplayName("白盒测试：未获取到主键时抛出异常")
    void getLongIdThrowsWhenNoKeyExists() {
        KeyHolder keyHolder = mock(KeyHolder.class);
        when(keyHolder.getKeyList()).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> GeneratedKeyUtils.getLongId(keyHolder, "id"));
    }
}
