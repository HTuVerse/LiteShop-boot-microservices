package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.restaurant.enums.PaperWidthEnum;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.StatusEnum;

/**
 * <p>
 * 打印模板配置表
 * 支持自定义小票排版：店铺信息、订单信息、菜品列表、底部信息
 * </p>
 *
 * @author Ht
 * @since 2025-01-27
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("print_template")
public class PrintTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 模板名称（如：默认模板、简约模板、详细模板）
     */
    private String templateName;

    /**
     * 关联的门店ID（为空表示全局模板）
     */
    private Long storeId;

    /**
     * 纸张宽度类型：58mm、80mm
     */
    private PaperWidthEnum paperWidth;

    /**
     * 是否显示店铺Logo：0-不显示，1-显示
     */
    private StatusEnum showLogo;

    /**
     * Logo图片URL
     */
    private String logoUrl;

    /**
     * 店铺名称显示样式：0-普通，1-加粗，2-加粗+居中，3-加粗+居中+放大
     */
    private Integer storeNameStyle;

    /**
     * 是否显示店铺地址：0-不显示，1-显示
     */
    private StatusEnum showAddress;

    /**
     * 是否显示联系电话：0-不显示，1-显示
     */
    private StatusEnum showPhone;

    /**
     * 是否显示营业时间：0-不显示，1-显示
     */
    private StatusEnum showBusinessHours;

    /**
     * 订单信息显示字段（JSON格式，如：["orderNo","tableName","orderType","createdAt"]）
     */
    private String orderInfoFields;

    /**
     * 菜品列表显示样式：0-简约（仅名称、数量、价格），1-详细（含规格、备注）
     */
    private Integer dishListStyle;

    /**
     * 是否显示菜品图片：0-不显示，1-显示（会影响打印速度）
     */
    private StatusEnum showDishImage;

    /**
     * 是否显示菜品规格：0-不显示，1-显示
     */
    private StatusEnum showDishSpec;

    /**
     * 是否显示菜品备注：0-不显示，1-显示
     */
    private StatusEnum showDishNote;

    /**
     * 是否显示订单备注：0-不显示，1-显示
     */
    private StatusEnum showOrderNote;

    /**
     * 是否显示支付信息：0-不显示，1-显示
     */
    private StatusEnum showPaymentInfo;

    /**
     * 底部欢迎语（如：谢谢惠顾，欢迎再次光临！）
     */
    private String footerText;

    /**
     * 是否显示二维码：0-不显示，1-显示
     */
    private StatusEnum showQrcode;

    /**
     * 二维码内容模板（支持变量：{orderNo}、{storeId}、{tableId}）
     * 示例：http://yourdomain.com/order/{orderNo}
     */
    private String qrcodeTemplate;

    /**
     * 二维码大小：1-小，2-中，3-大
     */
    private Integer qrcodeSize;

    /**
     * 字体大小：0-正常，1-中号，2-大号
     */
    private Integer fontSize;

    /**
     * 打印浓度（1-15，默认8）
     */
    private Integer printDensity;

    /**
     * 打印联数（默认1）
     */
    private Integer printCopies;

    /**
     * 是否为默认模板：0-否，1-是
     */
    private BooleanEnum isDefault;

    /**
     * 是否展示取餐码
     * */
    private BooleanEnum showPickupCode;

    /**
     * 是否启用：0-禁用，1-正常
     */
    private StatusEnum enabled;

    /**
     * 模板备注说明
     */
    private String remark;

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
