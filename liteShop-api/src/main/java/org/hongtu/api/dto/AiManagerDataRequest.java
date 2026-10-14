package org.hongtu.api.dto;

/**
 * AI 店长经营数据请求。
 *
 * @param scopeType 经营范围：TENANT 租户总部、STORE 单门店
 * @param storeId   门店范围下的门店ID
 * @param timeRange 时间范围：TODAY、YESTERDAY、WEEK、MONTH
 * @param dataMode  数据模式：BRIEFING 简报、DISH 菜品、DIAGNOSIS 诊断、ANALYSIS 扩展分析
 */
public record AiManagerDataRequest(String scopeType, Long storeId, String timeRange, String dataMode) {
}
