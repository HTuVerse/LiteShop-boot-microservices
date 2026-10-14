package org.hongtu.ai.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 仪表盘卡片。类型、数量、顺序和尺寸由模型按经营价值自行决定，服务端负责回填指标元数据。
 *
 * @param id           卡片唯一标识，供结论与证据相互引用
 * @param type         卡片类型：METRIC 指标、RISK 风险、OPPORTUNITY 机会、SUGGESTION 建议、NARRATIVE 叙述、CHART 图表、TABLE 表格
 * @param level        严重程度：INFO 信息、WARNING 需关注、OPPORTUNITY 机会
 * @param order        展示顺序，越小越靠前
 * @param size         布局尺寸：SM、MD、LG、XL
 * @param title        卡片标题
 * @param body         卡片结论文字
 * @param metricCode   关联指标编码，服务端据此回填指标名称与来源页
 * @param metricName   指标名称，由服务端补充
 * @param value        指标值
 * @param unit         指标单位
 * @param changeRate   环比变化率
 * @param sourcePage   证据下钻来源页，由服务端补充
 * @param evidenceRefs 关联的其它卡片 id，实现结论与支撑证据的关联
 * @param chart        图表定义，仅 CHART 卡片需要
 * @param table        表格定义，仅 TABLE 卡片需要
 */
public record AiDashboardCard(String id, String type, String level, Integer order, String size,
                              String title, String body, String metricCode, String metricName,
                              String value, String unit, BigDecimal changeRate, String sourcePage,
                              List<String> evidenceRefs, AiDashboardChart chart, AiDashboardTable table) {

    /** 兼容仍按旧参数构造（无表格）的调用方。 */
    public AiDashboardCard(String id, String type, String level, Integer order, String size,
                           String title, String body, String metricCode, String metricName,
                           String value, String unit, BigDecimal changeRate, String sourcePage,
                           List<String> evidenceRefs, AiDashboardChart chart) {
        this(id, type, level, order, size, title, body, metricCode, metricName, value, unit,
                changeRate, sourcePage, evidenceRefs, chart, null);
    }
}
