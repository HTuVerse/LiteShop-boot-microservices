package org.hongtu.ai.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 仪表盘图表的一组数据序列。
 *
 * @param name 序列名称
 * @param data 数据点，取值必须来自工具返回的真实数值
 */
public record AiDashboardChartSeries(String name, List<BigDecimal> data) {
}
