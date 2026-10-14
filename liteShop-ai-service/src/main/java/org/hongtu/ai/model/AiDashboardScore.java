package org.hongtu.ai.model;

import java.util.List;

/**
 * AI 经营诊断综合评分。属于模型的整体判断标签，不作为可下钻的经营数值。
 *
 * @param overall    总分，取值 0-100，由服务端裁剪
 * @param grade      等级，如 优秀/良好/一般/较差，由服务端按分值归一
 * @param comment    一句总体点评
 * @param dimensions 维度子评分集合，数量由服务端限制
 */
public record AiDashboardScore(Integer overall, String grade, String comment,
                               List<AiDashboardScoreDimension> dimensions) {
}
