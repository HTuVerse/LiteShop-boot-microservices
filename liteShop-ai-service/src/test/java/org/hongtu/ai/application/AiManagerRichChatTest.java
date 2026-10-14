package org.hongtu.ai.application;

import org.hongtu.ai.model.AiManagerResponse;
import org.hongtu.ai.model.ChatAnswerResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证智能问数富内容在服务端规整后可随回答保存。 */
class AiManagerRichChatTest {
    private final ObjectMapper json = JsonMapper.builder().build();

    @Test
    void mapsSupportedCardsAndFollowUpsIntoPersistedChatView() {
        ChatAnswerResult model = json.readValue("""
                {"answer":"**一店**销售额最高。","keyPoints":[],"evidence":[],"missingData":"","actions":[],
                 "tip":"  销售额以近7天为准。  ",
                 "cards":[
                   {"type":"RANKING","title":"门店销售排行","period":"近7天","items":[{"name":"一店","value":"128.6","unit":"万元","percent":120}]},
                   {"type":"INSIGHT_LIST","title":"原因","items":[{"title":"客单价","detail":"高于平均水平","icon":"ri:money-cny-circle-line"}]},
                   {"type":"CHART","title":"空图","chart":{"chartType":"LINE","categories":[],"series":[]}}
                 ],
                 "followUps":[" 查看近7天趋势？ ","查看近7天趋势？","比较本店订单数？","列出其他门店销售额？"]}
                """, ChatAnswerResult.class);

        AiManagerResponse response = AiManagerResultMapper.chat(model, Map.of("scopeType", "STORE"));

        assertNotNull(response.chatView());
        assertEquals("销售额以近7天为准。", response.chatView().tip());
        assertEquals(2, response.chatView().cards().size());
        assertEquals("RANKING", response.chatView().cards().getFirst().type());
        assertEquals(100, response.chatView().cards().getFirst().items().getFirst().percent().intValue());
        assertEquals("近7天", response.chatView().cards().getFirst().period());
        assertEquals(2, response.chatView().followUps().size());
        assertEquals("查看近7天趋势？", response.chatView().followUps().getFirst());
        assertTrue(json.writeValueAsString(response).contains("\"chatView\""));
    }

    @Test
    void dropsRankingWithoutNumericValuesAndKeepsPlainAnswer() {
        ChatAnswerResult model = json.readValue("""
                {"answer":"当前没有可核验的门店营业额。","missingData":"缺少本周期门店排行",
                 "cards":[{"type":"RANKING","title":"门店排行","period":"近30天",
                           "items":[{"name":"一店","value":"大约很多","unit":"元"}]}]}
                """, ChatAnswerResult.class);

        AiManagerResponse response = AiManagerResultMapper.chat(model, Map.of("scopeType", "TENANT"));

        assertEquals("当前没有可核验的门店营业额。\n\n还缺少的数据：缺少本周期门店排行", response.content());
        assertEquals(null, response.chatView());
    }

    @Test
    void dropsChartWithUnsupportedTypeInsteadOfRenderingDifferentChart() {
        ChatAnswerResult model = json.readValue("""
                {"answer":"订单数已查询。","cards":[{"type":"CHART","title":"订单趋势","period":"近7天",
                  "chart":{"chartType":"UNSUPPORTED","categories":["周一"],"series":[{"name":"订单","data":[12]}]}}]}
                """, ChatAnswerResult.class);

        AiManagerResponse response = AiManagerResultMapper.chat(model, Map.of("scopeType", "STORE"));

        assertEquals(null, response.chatView());
    }

    @Test
    void derivesRankingBarWidthsWhenModelOmitsPercent() {
        ChatAnswerResult model = json.readValue("""
                {"answer":"一店排名第一。","cards":[{"type":"RANKING","title":"排行","period":"近7天",
                  "items":[{"name":"一店","value":"20","unit":"元"},{"name":"二店","value":"10","unit":"元"}]}]}
                """, ChatAnswerResult.class);

        AiManagerResponse response = AiManagerResultMapper.chat(model, Map.of("scopeType", "TENANT"));

        assertEquals(100, response.chatView().cards().getFirst().items().get(0).percent().intValue());
        assertEquals(50, response.chatView().cards().getFirst().items().get(1).percent().intValue());
    }
}
