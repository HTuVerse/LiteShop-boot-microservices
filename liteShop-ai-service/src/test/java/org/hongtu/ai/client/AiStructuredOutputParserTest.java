package org.hongtu.ai.client;

import org.hongtu.ai.model.AiDashboardResult;
import org.hongtu.ai.model.BriefingResult;
import org.hongtu.ai.model.ChatAnswerResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证兼容解析器可以处理模型常见的 Markdown 和说明文字包装。 */
class AiStructuredOutputParserTest {
    @Test
    void extractsCompleteJsonObjectFromWrappedResponse() {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("""
                下面是结果：
                ```json
                {"keyPoints":[{"title":"正常","detail":"正常"}],
                 "evidence":[{"level":"INFO","title":"测试","detail":"测试"}],
                 "todayTasks":[{"title":"检查","description":"检查","risk":"无"}]}
                ```
                """))));

        BriefingResult result = AiStructuredOutputParser.parse(response, BriefingResult.class);

        assertEquals("正常", result.keyPoints().getFirst().title());
        assertEquals("检查", result.todayTasks().getFirst().title());
    }

    @Test
    void parsesDashboardResultWithCardsAndChart() {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"headline":"晚餐承压","report":"销售下滑","cards":[
                  {"id":"c1","type":"RISK","level":"WARNING","order":1,"size":"MD","title":"销售额下降","metricCode":"sales_drop_rate"},
                  {"id":"c2","type":"CHART","level":"INFO","order":2,"size":"LG","title":"趋势",
                   "chart":{"chartType":"LINE","categories":["周一","周二"],"series":[{"name":"销售额","data":[100,80]}],"unit":"元"}}
                ]}
                """))));

        AiDashboardResult result = AiStructuredOutputParser.parse(response, AiDashboardResult.class);

        assertEquals("晚餐承压", result.headline());
        assertEquals(2, result.cards().size());
        assertEquals("sales_drop_rate", result.cards().getFirst().metricCode());
        assertTrue(result.cards().get(1).chart().series().getFirst().data().contains(new java.math.BigDecimal("100")));
    }

    @Test
    void parsesDashboardResultWithScoreAndTable() {
        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"headline":"门店分化","report":"头部门店领先",
                 "score":{"overall":78,"grade":"良好","comment":"整体稳健","dimensions":[{"name":"营收","score":82,"comment":"好"}]},
                 "cards":[
                  {"id":"t1","type":"TABLE","level":"INFO","order":1,"size":"LG","title":"门店营业额",
                   "table":{"columns":[{"key":"store","label":"门店","align":"left","numeric":false},
                                       {"key":"amount","label":"营业额","align":"right","numeric":true}],
                            "rows":[["天河店","128.6"],["珠江店","96.2"]]}}
                ]}
                """))));

        AiDashboardResult result = AiStructuredOutputParser.parse(response, AiDashboardResult.class);

        assertEquals(78, result.score().overall());
        assertEquals("营收", result.score().dimensions().getFirst().name());
        assertEquals("TABLE", result.cards().getFirst().type());
        assertEquals(2, result.cards().getFirst().table().rows().size());
    }

    @Test
    void dashboardContractDescribesCardsChartScoreAndTable() {
        String contract = AiStructuredOutputParser.contract(AiDashboardResult.class);
        assertTrue(contract.contains("\"cards\""));
        assertTrue(contract.contains("\"chartType\""));
        assertTrue(contract.contains("\"score\""));
        assertTrue(contract.contains("\"table\""));
    }

    @Test
    void chatContractDescribesOptionalCardsAndFollowUps() {
        String contract = AiStructuredOutputParser.contract(ChatAnswerResult.class);
        assertTrue(contract.contains("\"tip\""));
        assertTrue(contract.contains("\"cards\""));
        assertTrue(contract.contains("\"followUps\""));
        assertTrue(contract.contains("\"missingData\""));
    }
}
