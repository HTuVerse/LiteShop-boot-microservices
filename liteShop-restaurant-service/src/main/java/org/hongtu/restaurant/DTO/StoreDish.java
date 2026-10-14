package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 门店菜品配置，保存总部菜品在具体门店的售价、库存和销售状态。
 */
@Data
@Accessors(chain = true)
@TableName("store_dish")
public class StoreDish implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 门店ID。 */
    private Long storeId;

    /** 菜品主数据ID。 */
    private Long dishId;

    /** 门店分类覆盖；为空时继承菜品默认分类。 */
    private Long categoryOverrideId;

    /** 门店正常售价覆盖；为空时继承总部正常售价。 */
    private BigDecimal salePriceOverride;

    /** 门店成本覆盖；为空时继承总部标准成本。 */
    private BigDecimal costPriceOverride;

    /** 销售状态：0下架，1上架，2清退。 */
    private Integer listingStatus;

    /** 是否临时售罄：0否，1是。 */
    private Integer soldOut;

    /** 库存模式：0不限库存，1跟踪库存。 */
    private Integer inventoryMode;

    /** 门店库存。 */
    private Integer stock;

    /** 门店推荐覆盖；为空时继承总部设置。 */
    private Integer recommendOverride;

    /** 门店热门覆盖；为空时继承总部设置。 */
    private Integer hotOverride;

    /** 门店新品覆盖；为空时继承总部设置。 */
    private Integer newOverride;

    /** 门店排序覆盖；为空时继承总部排序。 */
    private Integer sortOrderOverride;

    /** 门店累计销量。 */
    private Integer salesCount;

    /** 已继承的总部菜品版本。 */
    private Integer inheritedVersion;

    /** 门店配置版本，用于变更审计和并发控制。 */
    private Integer configVersion;

    /** 逻辑删除：0正常，1删除。 */
    private Integer deleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
