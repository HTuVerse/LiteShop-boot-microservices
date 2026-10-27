package org.hongtu.ai.application;

import org.junit.jupiter.api.Test;
import org.hongtu.ai.model.AiDashboardCard;
import org.hongtu.ai.model.AiDashboardChart;
import org.hongtu.ai.model.AiDashboardChartSeries;
import org.hongtu.ai.model.AiDashboardResult;
import org.hongtu.ai.model.ChatAnswerResult;
import org.hongtu.ai.model.ChatCard;
import org.hongtu.ai.model.ChatCardItem;
import org.hongtu.exception.BusinessException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void rejectsChartValueMissingFromCurrentToolFacts() {
        var facts = new AiDashboardFactRegistry(JsonMapper.builder().build());
        facts.record("getSalesOverview", "STORE", 12L, "WEEK", "{\"sales\":[100,80]}",
                LocalDateTime.of(2026, 10, 13, 9, 30));
        var chart = new AiDashboardChart("LINE", List.of("周一", "周二"),
                List.of(new AiDashboardChartSeries("销售额", List.of(
                        new BigDecimal("100"), new BigDecimal("81")))), "元");
        var card = new AiDashboardCard("sales", "CHART", "INFO", 1, "MD", "销售趋势", "",
                null, null, null, null, null, null, List.of(), chart, null);
        assertThrows(BusinessException.class, () -> facts.validateDashboard(
                new AiDashboardResult("趋势", "数据", List.of(card))));
    }

    @Test
    void rejectsChangeRateMissingFromCurrentToolFacts() {
        var facts = new AiDashboardFactRegistry(JsonMapper.builder().build());
        facts.record("getSalesOverview", "STORE", 12L, "WEEK",
                "{\"sales\":100,\"changeRate\":12.5}", LocalDateTime.of(2026, 10, 13, 9, 30));
        var card = new AiDashboardCard("sales", "METRIC", "INFO", 1, "SM", "销售额", "",
                null, null, "100", "元", new BigDecimal("13.5"), null, List.of(), null, null);
        assertThrows(BusinessException.class, () -> facts.validateDashboard(
                new AiDashboardResult("趋势", "数据", List.of(card))));
    }

    @Test
    void rejectsInventedRankingValueInChat() {
        var facts = new AiDashboardFactRegistry(JsonMapper.builder().build());
        facts.record("getSalesOverview", "STORE", 12L, "WEEK", "{\"sales\":100}",
                LocalDateTime.of(2026, 10, 13, 9, 30));
        var item = new ChatCardItem("一店", "101", "元", null, null, null, null);
        var card = new ChatCard("RANKING", "销售排行", "近7天", "sales_amount",
                List.of(item), null);
        var answer = new ChatAnswerResult("销售情况", List.of(), List.of(), "", List.of(), "",
                List.of(card), List.of());
        assertThrows(BusinessException.class, () -> facts.validateChat(answer));
    }
}
