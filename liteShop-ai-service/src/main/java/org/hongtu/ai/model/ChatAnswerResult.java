package org.hongtu.ai.model;

import java.util.List;

/**
 * 经营追问的模型结构化结果。
 *
 * @param answer      1 至 2 句话的总括结论
 * @param keyPoints   展开说明的结构化要点，没有可拆分内容时返回空数组
 * @param evidence    回答引用的经营证据
 * @param missingData 仍缺少、因此不能下结论的数据
 * @param actions      可转成人工任务的建议动作，没有合适动作时返回空数组
 * @param tip          可选的小贴士文字
 * @param cards        按本轮工具数据选择的展示卡片
 * @param followUps    当前会话中可继续提问的问题
 */
public record ChatAnswerResult(String answer, List<AiTitledDetail> keyPoints,
                               List<AiEvidenceItem> evidence, String missingData,
                               List<AiActionItem> actions, String tip, List<ChatCard> cards,
                               List<String> followUps) {
}
