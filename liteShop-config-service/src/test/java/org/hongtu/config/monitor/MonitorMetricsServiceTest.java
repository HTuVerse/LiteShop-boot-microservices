package org.hongtu.config.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证 Prometheus 异常样本不会被误报为零值。 */
class MonitorMetricsServiceTest {
    @Test
    void trendDropsNonFiniteSamples() throws Exception {
        String response = "[{\"values\":[[1,\"NaN\"],[2,\"+Inf\"],[3,\"7.5\"]]}]";
        assertEquals(List.of(Map.of("time", 3L, "value", 7.5)),
                MonitorMetricsService.finitePoints(new ObjectMapper().readTree(response)));
    }
}
