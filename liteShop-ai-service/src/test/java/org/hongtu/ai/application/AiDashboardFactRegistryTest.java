package org.hongtu.ai.application;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证模型只能引用本次只读工具实际返回的数字。 */
class AiDashboardFactRegistryTest {
    @Test
    void resolvesOnlyRecordedNumericPathAndPreservesZero() {
        var facts = new AiDashboardFactRegistry(JsonMapper.builder().build());
        var sourceId = facts.record("getOperatingDiagnosis", "STORE", 12L, "WEEK",
                "{\"overview\":{\"sales\":0,\"missing\":null},\"series\":[12,18]}",
                LocalDateTime.of(2026, 10, 13, 9, 30));

        assertTrue(facts.hasNumericData());
        assertEquals(new BigDecimal("0"), facts.resolve(sourceId, "/overview/sales").value());
        assertEquals(new BigDecimal("18"), facts.resolve(sourceId, "/series/1").value());
        assertEquals("STORE", facts.resolve(sourceId, "/series/1").scopeType());
        assertEquals("WEEK", facts.resolve(sourceId, "/series/1").timeRange());
        assertNull(facts.resolve(sourceId, "/overview/missing"));
        assertNull(facts.resolve(sourceId, "/overview/notFound"));
        assertNull(facts.resolve("other-request", "/overview/sales"));
    }
}
