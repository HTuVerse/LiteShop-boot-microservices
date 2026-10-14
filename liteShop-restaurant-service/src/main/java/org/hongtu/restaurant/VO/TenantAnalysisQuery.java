package org.hongtu.restaurant.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 租户经营分析统一查询条件。
 */
@Data
public class TenantAnalysisQuery {

    /** 时间范围：TODAY、YESTERDAY、WEEK、MONTH、YEAR、CUSTOM。 */
    private String timeRange = "TODAY";

    /** 自定义开始日期，仅 CUSTOM 使用。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /** 自定义结束日期，仅 CUSTOM 使用。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 门店范围：ALL 全部门店，GROUP 门店分组。 */
    private String scopeType = "ALL";

    /** 门店分组ID，scopeType=GROUP 时必填。 */
    private Long groupId;

    /** 排名指标：GROSS_SALES、NET_SALES、ORDER_COUNT、AVG_ORDER、QUANTITY、PROFIT。 */
    private String metric;

    /** 返回榜单数量。 */
    private Integer limit = 5;

    /** 矩阵菜品页码。 */
    private Integer currentPage = 1;

    /** 矩阵菜品分页大小。 */
    private Integer pageSize = 10;

    /** 门店菜品横向比较中选中的菜品ID。 */
    private Long dishId;
}
