package org.hongtu.restaurant.VO;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门店菜品个性化配置请求。
 */
@Data
@Accessors(chain = true)
public class StoreDishConfigRequest {

    private Long storeId;
    private Long dishId;
    private Long categoryOverrideId;
    private BigDecimal salePriceOverride;
    private BigDecimal costPriceOverride;

    /** 销售状态：0下架，1上架，2清退。 */
    private Integer listingStatus;

    /** 是否临时售罄：0否，1是。 */
    private Integer soldOut;

    /** 库存模式：0不限库存，1跟踪库存。 */
    private Integer inventoryMode;
    private Integer stock;
    private Integer recommendOverride;
    private Integer hotOverride;
    private Integer newOverride;
    private Integer sortOrderOverride;

    /** 提交时看到的门店配置版本；为空时兼容旧调用方，不做版本校验。 */
    private Integer expectedConfigVersion;

    /** 计划生效时间；为空表示审批后立即生效。 */
    private LocalDateTime effectiveAt;

    /** 变更原因。 */
    private String changeReason;
}
