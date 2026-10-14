package org.hongtu.ai.model;

import java.util.List;

/**
 * 仪表盘表格定义，仅 TABLE 卡片填充。单元格取值必须来自工具返回的真实数据，
 * 数值列由服务端校验，列数与行数均有上限。
 *
 * @param columns 列定义集合
 * @param rows    数据行，每行单元格数量与列数对齐
 */
public record AiDashboardTable(List<AiDashboardTableColumn> columns, List<List<String>> rows) {
}
