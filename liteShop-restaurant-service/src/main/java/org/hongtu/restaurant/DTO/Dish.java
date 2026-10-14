package org.hongtu.restaurant.DTO;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.restaurant.enums.SceneTypeEnum;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.OnOrOffShelfEnum;

/**
 * <p>
 * 菜品表
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("dish")
public class Dish implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 租户内稳定菜品编码。
     */
    private String dishCode;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 菜品来源：0-总部统一菜品，1-门店自建菜品。
     */
    private Integer sourceType;

    /**
     * 门店自建菜品的所属门店；总部菜品为空。
     */
    private Long ownerStoreId;

    /**
     * 总部状态：0-草稿，1-启用，2-全局停用。
     */
    private Integer masterStatus;

    /**
     * 总部菜品版本，用于标识门店继承到的版本。
     */
    private Integer version;

    /**
     * 逻辑删除：0-正常，1-删除。
     */
    private Integer deleted;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 菜品名称
     */
    private String dishName;

    /**
     * 菜品描述
     */
    private String dishDesc;

    /**
     * 菜品图片（JSON数组）
     */
    private String dishPics;

    /**
     * 总部正常售价。活动价、会员价均由统一定价服务计算，不落在菜品主表。
     */
    private BigDecimal salePrice;

    /**
     * 成本价
     */
    private BigDecimal costPrice;

    /**
     * 单位
     */
    private String unit;

    /**
     * 库存数量
     */
    private Integer stock;

    /**
     * 销量
     */
    private Integer salesCount;

    /**
     * 浏览量
     */
    private Integer viewCount;

    /**
     * 是否推荐：0-否，1-是
     */
    private BooleanEnum isRecommend;

    /**
     * 是否热门：0-否，1-是
     */
    private BooleanEnum isHot;

    /**
     * 是否新品：0-否，1-是
     */
    private BooleanEnum isNew;

    /**
     * 适用场景：1-仅外卖，2-仅堂食，3-通用
     */
    private SceneTypeEnum sceneType;

    /**
     * 销售状态：0-下架，1-上架
     */
    private OnOrOffShelfEnum saleStatus;

    /**
     * 排序值，数字越小越靠前
     */
    private Integer sortOrder;

    /**
     * 商品标签（JSON数组）
     */
    private String tags;

    /**
     * 过敏原信息
     */
    private String allergens;

    /**
     * 营养信息（JSON）
     */
    private String nutritionInfo;

    /**
     * 制作时间（分钟）
     */
    private Integer cookingTime;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;


}
