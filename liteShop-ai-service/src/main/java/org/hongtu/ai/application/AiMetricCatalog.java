package org.hongtu.ai.application;

import org.hongtu.ai.model.AiMetricDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** AI 店长统一指标目录，集中维护指标编码、单位、口径和来源页面。 */
public final class AiMetricCatalog {
    private static final Map<String, AiMetricDefinition> METRICS = createMetrics();

    private AiMetricCatalog() {
    }

    public static List<AiMetricDefinition> all() {
        return List.copyOf(METRICS.values());
    }

    public static AiMetricDefinition find(String code) {
        return code == null ? null : METRICS.get(code.trim().toLowerCase(Locale.ROOT));
    }

    public static String sourcePage(AiMetricDefinition metric, String scopeType) {
        if (metric == null) return null;
        return "STORE".equalsIgnoreCase(scopeType)
                ? metric.storeSourcePage() : metric.tenantSourcePage();
    }

    private static Map<String, AiMetricDefinition> createMetrics() {
        Map<String, AiMetricDefinition> result = new LinkedHashMap<>();
        add(result, "sales_amount", "销售额", "元", "SALES", "已完成经营订单的实收销售金额");
        add(result, "sales_drop_rate", "销售下降率", "%", "SALES", "当前周期销售额相对上一相邻周期的下降比例");
        add(result, "order_count", "订单数", "单", "ORDER", "统计周期内的经营订单数量");
        add(result, "average_order_value", "客单价", "元/单", "SALES", "销售额除以有效订单数");
        add(result, "gross_profit_rate", "毛利率", "%", "PROFIT", "销售收入扣除已录入成本后的毛利占比");
        add(result, "cancel_rate", "订单取消率", "%", "ORDER", "取消订单数占全部订单数的比例");
        add(result, "discount_cost_rate", "优惠成本率", "%", "MARKETING", "优惠金额占活动相关销售额的比例");
        add(result, "low_stock_dishes", "库存风险菜品", "项", "INVENTORY", "达到低库存阈值或已售罄的菜品数量");
        add(result, "repeat_rate", "会员复购率", "%", "MEMBER", "统计周期内复购会员占已识别会员的比例");
        add(result, "dormant_rate", "沉睡会员率", "%", "MEMBER", "沉睡会员占已识别会员的比例");
        add(result, "dish_sales", "菜品销量", "份", "DISH", "统计周期内菜品售出数量");
        add(result, "returned_dish_amount", "退菜金额", "元", "ORDER", "退菜明细对应的金额汇总");
        add(result, "table_turnover", "桌台翻台", "次", "EFFICIENCY", "活跃桌台在统计周期内的平均使用次数");
        return result;
    }

    private static void add(Map<String, AiMetricDefinition> target, String code, String name,
                            String unit, String domain, String description) {
        boolean decrease = Set.of("sales_drop_rate", "cancel_rate", "discount_cost_rate",
                "low_stock_dishes", "dormant_rate", "returned_dish_amount").contains(code);
        // 库存风险样本当前最多返回20项，不能把截断样本当作任务效果总量。
        boolean reviewable = !Set.of("gross_profit_rate", "dish_sales", "low_stock_dishes").contains(code);
        target.put(code, new AiMetricDefinition(code, name, unit, domain, description,
                decrease ? "DECREASE" : "INCREASE", reviewable,
                "/dashboard/tenantAnalyze", "/dashboard/restaurantAnalyze"));
    }
}
