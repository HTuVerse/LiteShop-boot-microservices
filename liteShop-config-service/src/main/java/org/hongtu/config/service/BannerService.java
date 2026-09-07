package org.hongtu.config.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.config.DTO.Banner;
import org.hongtu.config.enums.BannerPositionEnum;

import java.util.List;

/**
 * 轮播图配置 Service 接口
 *
 * @author Ht
 */
public interface BannerService extends IService<Banner> {

    /**
     * 根据位置获取启用的轮播图列表
     *
     * @param position 轮播图位置
     * @return 轮播图列表
     */
    List<Banner> getEnabledBannersByPosition(BannerPositionEnum position);

    /**
     * 获取首页轮播图
     *
     * @return 轮播图列表
     */
    List<Banner> getHomeBanners();

    /**
     * 获取点餐页轮播图
     *
     * @return 轮播图列表
     */
    List<Banner> getOrderBanners();

    /**
     * 更新轮播图状态
     *
     * @param id     轮播图ID
     * @param status 状态
     * @return 是否成功
     */
    boolean updateStatus(Long id, Integer status);

    /**
     * 更新轮播图排序
     *
     * @param id        轮播图ID
     * @param sortOrder 排序值
     * @return 是否成功
     */
    boolean updateSortOrder(Long id, Integer sortOrder);
}
