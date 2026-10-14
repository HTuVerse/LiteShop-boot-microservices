package org.hongtu;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.hongtu.restaurant.DAO.RestaurantAnalysisMapper;
import org.hongtu.restaurant.VO.analysis.DishChangeStatisticsVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL严格分组模式下的加退菜查询回归测试，仅使用连接级临时表。
 * 通过-Dliteshop.analysis-test.jdbc-url指定独立测试库，不启动业务服务。
 */
@EnabledIfSystemProperty(named = "liteshop.analysis-test.jdbc-url", matches = ".+")
class RestaurantAnalysisMapperMysqlTest {
    private Connection connection;
    private SqlSession session;
    private RestaurantAnalysisMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection(
                System.getProperty("liteshop.analysis-test.jdbc-url"),
                System.getProperty("liteshop.analysis-test.jdbc-user", "root"),
                System.getProperty("liteshop.analysis-test.jdbc-password", ""));
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION sql_mode = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES'");
            // 临时表只在本次连接内可见，关闭连接即清理，不改业务表。
            statement.execute("""
                    CREATE TEMPORARY TABLE dish (
                      id BIGINT PRIMARY KEY COMMENT '菜品ID',
                      dish_name VARCHAR(100) COMMENT '菜品名称'
                    ) COMMENT='测试菜品'
                    """);
            statement.execute("""
                    CREATE TEMPORARY TABLE dish_category (
                      id BIGINT PRIMARY KEY COMMENT '分类ID',
                      category_name VARCHAR(50) COMMENT '分类名称'
                    ) COMMENT='测试分类'
                    """);
            statement.execute("""
                    CREATE TEMPORARY TABLE order_dish_change_log (
                      id BIGINT PRIMARY KEY COMMENT '事件ID',
                      dish_id BIGINT COMMENT '菜品快照ID',
                      category_id BIGINT COMMENT '分类快照ID',
                      store_id BIGINT COMMENT '门店ID',
                      change_type INT COMMENT '1加菜，2退菜',
                      reason VARCHAR(255) COMMENT '变更原因',
                      order_id BIGINT COMMENT '订单ID',
                      quantity INT COMMENT '变更数量，正数',
                      amount DECIMAL(12,2) COMMENT '变更金额，单位元',
                      occurred_at DATETIME COMMENT '发生时间'
                    ) COMMENT='测试加退菜事件'
                    """);
            statement.execute("INSERT INTO dish VALUES (1,'招牌牛肉饭')");
            statement.execute("INSERT INTO dish_category VALUES (1,'主食')");
            statement.execute("""
                    INSERT INTO order_dish_change_log VALUES
                      (1,1,1,1,1,'追加一份',101,1,10,'2026-09-15 12:00:00'),
                      (2,1,1,1,1,'追加两份',102,2,20,'2026-09-15 12:01:00'),
                      (3,1,1,1,1,NULL,102,3,30,'2026-09-15 12:02:00'),
                      (4,1,1,1,2,'口味不符',103,1,10,'2026-09-15 12:03:00'),
                      (5,1,1,1,2,'口味不符',103,2,20,'2026-09-15 12:04:00'),
                      (6,1,1,1,2,NULL,104,1,10,'2026-09-15 12:05:00'),
                      (7,1,1,1,2,'未填写原因',105,2,20,'2026-09-15 12:06:00'),
                      (8,1,1,2,2,'其他门店',106,100,1000,'2026-09-15 12:00:00'),
                      (9,1,1,1,2,'超出日期',107,100,1000,'2026-08-31 12:00:00'),
                      (10,99,NULL,3,2,NULL,108,1,15,'2026-09-15 12:00:00')
                    """);
        }
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/RestaurantAnalysisMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(connection);
        mapper = session.getMapper(RestaurantAnalysisMapper.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (session != null) {
            session.close();
        }
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    void additionsCombineDifferentReasonsWithoutViolatingStrictGrouping() {
        List<DishChangeStatisticsVO> result = query(1L, 1);
        assertEquals(1, result.size());
        DishChangeStatisticsVO row = result.getFirst();
        assertNull(row.getReason());
        assertEquals(1, row.getChangeType());
        assertEquals(2, row.getOrderCount());
        assertEquals(3, row.getChangeCount());
        assertEquals(6, row.getQuantity());
        assertEquals(new BigDecimal("60.00"), row.getAmount());
    }

    @Test
    void refundsGroupByReasonAndRespectStoreAndDateFilters() {
        List<DishChangeStatisticsVO> result = query(1L, 2);
        assertEquals(2, result.size());
        DishChangeStatisticsVO taste = result.stream()
                .filter(row -> "口味不符".equals(row.getReason())).findFirst().orElseThrow();
        DishChangeStatisticsVO missing = result.stream()
                .filter(row -> "未填写原因".equals(row.getReason())).findFirst().orElseThrow();
        assertEquals(1, taste.getOrderCount());
        assertEquals(2, missing.getOrderCount());
        assertTrue(result.stream().allMatch(row -> row.getChangeType() == 2
                && row.getChangeCount() == 2 && row.getQuantity() == 3
                && new BigDecimal("30.00").equals(row.getAmount())));
    }

    @Test
    void deletedDishAndMissingCategoryKeepTheirEventStatistics() {
        List<DishChangeStatisticsVO> result = query(3L, 2);
        assertEquals(1, result.size());
        assertEquals("菜品#99", result.getFirst().getDishName());
        assertEquals("未分类", result.getFirst().getCategoryName());
        assertEquals("未填写原因", result.getFirst().getReason());
        assertEquals(new BigDecimal("15.00"), result.getFirst().getAmount());
    }

    private List<DishChangeStatisticsVO> query(Long storeId, Integer changeType) {
        return mapper.getDishChangeStatistics(storeId,
                "2026-09-01 00:00:00", "2026-09-30 23:59:59", changeType);
    }
}
