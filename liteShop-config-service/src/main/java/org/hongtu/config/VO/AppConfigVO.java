package org.hongtu.config.VO;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 小程序端配置VO
 *
 * @author Ht
 */
@Data
public class AppConfigVO {

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用Logo
     */
    private String appLogo;

    /**
     * 应用描述
     */
    private String appDesc;

    /**
     * 首页背景图片
     */
    private String homeBackgroundImage;

    /**
     * 点餐背景图片
     */
    private String orderBackgroundImage;

    /**
     * 默认菜品图片
     */
    private String defaultDishImage;

    /**
     * 默认头像
     */
    private String defaultAvatar;

    /**
     * 默认空图片（无数据时显示）
     */
    private String defaultEmptyImage;

    /**
     * 默认订单图片
     */
    private String defaultOrderImage;

    /**
     * 主题色
     */
    private String themeColor;

    /**
     * 客服电话
     */
    private String servicePhone;

    /**
     * 首页轮播图列表
     */
    private List<BannerVO> homeBanners;

    /**
     * 点餐页轮播图列表
     */
    private List<BannerVO> orderBanners;

    /**
     * 其他扩展配置
     */
    private Map<String, String> extraConfig;
}
