package org.hongtu.ai.model;

import java.util.List;

/**
 * 仪表盘图表定义，数据点必须来自工具返回的真实序列，由模型按数据结构选择图表类型。
 *
 * @param chartType  图表类型：LINE 折线、BAR 柱状、HBAR 条形、RING 环形、FUNNEL 漏斗、HEATMAP 热力
 * @param categories 维度或 X 轴分类
 * @param series     数据序列
 * @param unit       数值单位
 */
public record AiDashboardChart(String chartType, List<String> categories,
                               List<AiDashboardChartSeries> series, String unit) {
}
