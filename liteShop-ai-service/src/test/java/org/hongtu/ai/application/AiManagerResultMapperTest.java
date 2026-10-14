package org.hongtu.ai.application;

import org.hongtu.ai.model.AiDashboardCard;
import org.hongtu.ai.model.AiDashboardChart;
import org.hongtu.ai.model.AiDashboardChartSeries;
import org.hongtu.ai.model.AiDashboardResult;
import org.hongtu.ai.model.AiDashboardScore;
import org.hongtu.ai.model.AiDashboardScoreDimension;
import org.hongtu.ai.model.AiDashboardTable;
import org.hongtu.ai.model.AiDashboardTableColumn;
import org.hongtu.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证诊断快照不会把缺失数据伪装成有效数据。 */
class AiManagerResultMapperTest {
    @Test
    void nullChartPointDoesNotBecomeZero() {
        var risk = new AiDashboardCard("risk", "RISK", "WARNING", 1, "MD", "销售变化", "需核对来源",
                null, null, null, null, null, null, List.of(), null);
        var chart = new AiDashboardChart("LINE", List.of("周一", "周二"),
                List.of(new AiDashboardChartSeries("销售额", java.util.Arrays.asList(BigDecimal.TEN, null))), "元");
        var chartCard = new AiDashboardCard("chart", "CHART", "INFO", 2, "LG", "销售趋势", null,
                null, null, null, null, null, null, List.of(), chart);

        var result = AiManagerResultMapper.dashboard(
                new AiDashboardResult("销售变化", "需要核对", List.of(risk, chartCard)), Map.of());

        assertEquals(1, result.dashboard().cards().size());
        assertEquals("RISK", result.dashboard().cards().getFirst().type());
    }

    @Test
    void textOnlyResultIsNotCompleteWhenRealDataExists() {
        var narrative = new AiDashboardCard("text", "NARRATIVE", "INFO", 1, "MD", "经营说明", "请关注经营变化",
                null, null, null, null, null, null, List.of(), null);

        assertThrows(BusinessException.class, () -> AiManagerResultMapper.dashboard(
                new AiDashboardResult("经营说明", "请关注经营变化", List.of(narrative)),
                Map.of("dataAvailable", true)));
    }

    @Test
    void scoreIsClampedAndGradeDerivedFromOverall() {
        var dimensions = List.of(new AiDashboardScoreDimension("营收", 120, "超高"),
                new AiDashboardScoreDimension("风险", -5, "超低"));
        var score = new AiDashboardScore(150, "随便填", "总体不错", dimensions);
        var metric = new AiDashboardCard("m", "METRIC", "INFO", 1, "MD", "营业额", null,
                "sales_amount", null, "128.6", "元", null, null, List.of(), null);

        var result = AiManagerResultMapper.dashboard(
                new AiDashboardResult("总体不错", "报告", score, List.of(metric)), Map.of());

        var mapped = result.dashboard().score();
        assertNotNull(mapped);
        assertEquals(100, mapped.overall());
        assertEquals("优秀", mapped.grade());
        assertEquals(100, mapped.dimensions().get(0).score());
        assertEquals(0, mapped.dimensions().get(1).score());
    }

    @Test
    void legacySnapshotWithoutScoreMapsToNull() {
        var metric = new AiDashboardCard("m", "METRIC", "INFO", 1, "MD", "营业额", null,
                "sales_amount", null, "128.6", "元", null, null, List.of(), null);

        var result = AiManagerResultMapper.dashboard(
                new AiDashboardResult("标题", "报告", List.of(metric)), Map.of());

        assertNull(result.dashboard().score());
    }

    @Test
    void tableColumnsAndRowsAreBounded() {
        List<AiDashboardTableColumn> columns = new ArrayList<>();
        for (int i = 0; i < 8; i++) columns.add(new AiDashboardTableColumn("c" + i, "列" + i, "left", false));
        List<List<String>> rows = new ArrayList<>();
        for (int r = 0; r < 15; r++) {
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < 8; c++) cells.add("v" + r + "-" + c);
            rows.add(cells);
        }
        var tableCard = new AiDashboardCard("t", "TABLE", "INFO", 1, "LG", "门店明细", null,
                null, null, null, null, null, null, List.of(), null, new AiDashboardTable(columns, rows));

        var result = AiManagerResultMapper.dashboard(
                new AiDashboardResult("门店明细", "报告", List.of(tableCard)), Map.of("dataAvailable", true));

        var table = result.dashboard().cards().getFirst().table();
        assertNotNull(table);
        assertEquals(6, table.columns().size());
        assertEquals(10, table.rows().size());
        assertEquals(6, table.rows().getFirst().size());
    }

    @Test
    void tableWithInvalidNumericCellDegradesToNarrative() {
        var columns = List.of(new AiDashboardTableColumn("store", "门店", "left", false),
                new AiDashboardTableColumn("amount", "营业额", "right", true));
        var rows = List.of(List.of("天河店", "不是数字"));
        var tableCard = new AiDashboardCard("t", "TABLE", "INFO", 1, "LG", "门店明细", "数据待核对",
                null, null, null, null, null, null, List.of(), null, new AiDashboardTable(columns, rows));

        var result = AiManagerResultMapper.dashboard(
                new AiDashboardResult("门店明细", "报告", List.of(tableCard)), Map.of());

        var card = result.dashboard().cards().getFirst();
        assertEquals("NARRATIVE", card.type());
        assertNull(card.table());
    }

    @Test
    void validTableCountsAsVerifiableDataBlock() {
        var columns = List.of(new AiDashboardTableColumn("store", "门店", "left", false),
                new AiDashboardTableColumn("amount", "营业额", "right", true));
        var rows = List.of(List.of("天河店", "128.6"), List.of("珠江店", "96.2"));
        var tableCard = new AiDashboardCard("t", "TABLE", "INFO", 1, "LG", "门店营业额", null,
                null, null, null, null, null, null, List.of(), null, new AiDashboardTable(columns, rows));

        // dataAvailable=true 时，只要有合法表格就不应抛“无可核验数据”异常。
        var result = AiManagerResultMapper.dashboard(
                new AiDashboardResult("门店营业额", "报告", List.of(tableCard)), Map.of("dataAvailable", true));

        var card = result.dashboard().cards().getFirst();
        assertEquals("TABLE", card.type());
        assertEquals(2, card.table().rows().size());
        assertFalse(result.evidence().stream().anyMatch(item -> "门店营业额".equals(item.title())));
    }
}
