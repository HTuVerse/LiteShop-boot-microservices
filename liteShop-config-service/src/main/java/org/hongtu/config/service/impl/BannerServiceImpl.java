package org.hongtu.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.hongtu.config.DAO.BannerMapper;
import org.hongtu.config.DTO.Banner;
import org.hongtu.config.enums.BannerPositionEnum;
import org.hongtu.config.service.BannerService;
import org.hongtu.enums.BooleanEnum;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 轮播图配置 Service 实现类
 *
 * @author Ht
 */
@Service
public class BannerServiceImpl extends ServiceImpl<BannerMapper, Banner> implements BannerService {

    @Override
    public List<Banner> getEnabledBannersByPosition(BannerPositionEnum position) {
        LocalDateTime now = LocalDateTime.now();
        
        LambdaQueryWrapper<Banner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Banner::getPosition, position)
                .eq(Banner::getStatus, BooleanEnum.YES)
                // 检查时间范围（如果设置了的话）
                .and(w -> w
                        .isNull(Banner::getStartTime)
                        .or()
                        .le(Banner::getStartTime, now)
                )
                .and(w -> w
                        .isNull(Banner::getEndTime)
                        .or()
                        .ge(Banner::getEndTime, now)
                )
                .orderByAsc(Banner::getSortOrder)
                .orderByDesc(Banner::getCreatedAt);
        
        return list(wrapper);
    }

    @Override
    public List<Banner> getHomeBanners() {
        return getEnabledBannersByPosition(BannerPositionEnum.HOME_BANNER);
    }

    @Override
    public List<Banner> getOrderBanners() {
        return getEnabledBannersByPosition(BannerPositionEnum.ORDER_BANNER);
    }

    @Override
    public boolean updateStatus(Long id, Integer status) {
        LambdaUpdateWrapper<Banner> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Banner::getId, id)
                .set(Banner::getStatus, BooleanEnum.fromCode(status))
                .set(Banner::getUpdatedAt, LocalDateTime.now());
        return update(wrapper);
    }

    @Override
    public boolean updateSortOrder(Long id, Integer sortOrder) {
        LambdaUpdateWrapper<Banner> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Banner::getId, id)
                .set(Banner::getSortOrder, sortOrder)
                .set(Banner::getUpdatedAt, LocalDateTime.now());
        return update(wrapper);
    }
}
