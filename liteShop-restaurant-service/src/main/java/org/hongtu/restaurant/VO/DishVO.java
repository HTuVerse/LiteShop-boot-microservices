package org.hongtu.restaurant.VO;

import lombok.Data;
import org.hongtu.restaurant.DTO.DishSpecOption;
import org.hongtu.restaurant.enums.SceneTypeEnum;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.OnOrOffShelfEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜品信息VO
 * @author Ht
 * @since 2025-09-19
 */
@Data
public class DishVO {

    /**
     * 主键ID
     */
    private Long id;

    /** 门店菜品配置ID。 */
    private Long storeDishId;

    /** 租户内菜品编码。 */
    private String dishCode;

    /**
     * 门店ID
     */
    private Long storeId;

    /** 菜品来源：0总部，1门店自建。 */
    private Integer sourceType;

    /** 门店自建菜品所属门店。 */
    private Long ownerStoreId;

    /** 总部状态：0草稿，1启用，2全局停用。 */
    private Integer masterStatus;

    /** 总部菜品版本。 */
    private Integer version;

    /**
     * 分类ID
     */
    private Long categoryId;

    /** 门店分类覆盖ID。 */
    private Long categoryOverrideId;

    /**
     * 分类名称
     */
    private String categoryName;

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

    /** 与菜品图片按顺序对应的资源 ID。 */
    private String dishPicResourceIds;

    /**
     * 主图
     */
    private String mainPic;

    /** 总部正常售价。 */
    private BigDecimal salePrice;

    /** 门店正常售价覆盖；为空表示继承总部售价。 */
    private BigDecimal salePriceOverride;

    /** 门店覆盖后的正常售价。 */
    private BigDecimal effectiveSalePrice;

    /** 当前命中的活动或会员价格；未命中时为空。 */
    private BigDecimal campaignPrice;

    /** 当前菜单展示价；结算仍须以后端试价结果为准。 */
    private BigDecimal displayPrice;

    /** 价格来源：HEADQUARTERS、STORE_OVERRIDE、MEMBER_PRICE、PROMOTION。 */
    private String priceSource;

    /** 当前命中的定价活动。 */
    private Long appliedCampaignId;
    private String appliedCampaignName;

    /** 当前菜品可参加的活动，供收银台菜品卡片展示。 */
    private List<PricingQuoteVO.CampaignHint> availableCampaigns;

    /**
     * 成本价
     */
    private BigDecimal costPrice;

    /** 门店成本覆盖。 */
    private BigDecimal costPriceOverride;

    /**
     * 单位
     */
    private String unit;

    /**
     * 库存数量
     */
    private Integer stock;

    /** 库存模式：0不限库存，1跟踪库存。 */
    private Integer inventoryMode;

    /** 门店销售状态：0下架，1上架，2清退。 */
    private Integer listingStatus;

    /** 门店自身配置状态；总部全局停用时可与最终销售状态不同。 */
    private Integer storeListingStatus;

    /** 是否临时售罄。 */
    private Integer soldOut;

    /** 当前展示值是否使用了门店覆盖价格。 */
    private Boolean salePriceOverridden;

    private Integer recommendOverride;
    private Integer hotOverride;
    private Integer newOverride;
    private Integer sortOrderOverride;

    /** 门店配置版本，用于审批和定时发布时的乐观并发校验。 */
    private Integer configVersion;

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
     * 规格选项列表
     */
    private List<DishSpecOption> specOptions;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

}
