package org.hongtu.config.VO;

import lombok.Data;

/**
 * 轮播图VO
 *
 * @author Ht
 */
@Data
public class BannerVO {

    /**
     * 轮播图ID
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 图片地址
     */
    private String imageUrl;

    /** 资源库图片 ID，旧客户端仍使用 imageUrl。 */
    private Long imageResourceId;

    /**
     * 跳转链接
     */
    private String linkUrl;

    /**
     * 跳转类型：1-小程序页面 2-H5链接 3-不跳转
     */
    private Integer linkType;
}
