package org.hongtu.ai.model;

/**
 * 仪表盘表格列定义，列由模型按已登记数据字段决定，服务端限制数量并校验。
 *
 * @param key     列键，对应行单元格顺序，可空时按列顺序对齐
 * @param label   列标题
 * @param align   对齐方式：left、center、right，默认 left
 * @param numeric 是否数值列：true 时单元格必须是可解析的数字
 */
public record AiDashboardTableColumn(String key, String label, String align, Boolean numeric) {
}
