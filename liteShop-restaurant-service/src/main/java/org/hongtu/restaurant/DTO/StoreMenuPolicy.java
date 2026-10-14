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
 * 门店菜单策略，控制门店是否可以自建菜品、改价和自主下架。
 */
@Data
@Accessors(chain = true)
@TableName("store_menu_policy")
public class StoreMenuPolicy implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 门店ID。 */
    private Long storeId;

    /** 是否允许门店自建菜品。 */
    private Integer allowLocalDish;

    /** 是否允许门店自建分类。 */
    private Integer allowLocalCategory;

    /** 是否允许门店调整菜品所属分类。 */
    private Integer allowCategoryOverride;

    /** 是否允许门店覆盖价格。 */
    private Integer allowPriceOverride;

    /** 是否允许门店自主下架。 */
    private Integer allowStoreOffShelf;

    /** 是否允许门店清退总部菜品。 */
    private Integer allowStoreExit;

    /** 最低售价相对总部价格的比例。 */
    private BigDecimal minPriceRate;

    /** 最高售价相对总部价格的比例。 */
    private BigDecimal maxPriceRate;

    /** 门店发起菜品配置变更时是否需要总部审批。 */
    private Integer requireApproval;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
