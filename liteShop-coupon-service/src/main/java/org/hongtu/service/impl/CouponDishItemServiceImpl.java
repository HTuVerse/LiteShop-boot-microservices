package org.hongtu.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.CouponDishItemMapper;
import org.hongtu.DTO.CouponDishItem;
import org.hongtu.service.CouponDishItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 优惠券-菜品关联表 服务实现类
 * </p>
 *
 * @author Ht
 * @since 2026-02-07
 */
@Slf4j
@Service
public class CouponDishItemServiceImpl extends ServiceImpl<CouponDishItemMapper, CouponDishItem> implements CouponDishItemService {

    @Resource
    private CouponDishItemMapper couponDishItemMapper;

    @Override
    public List<CouponDishItem> getItemsByCouponId(Long couponId) {
        return couponDishItemMapper.selectByCouponId(couponId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveItems(Long couponId, List<CouponDishItem> items) {
        // 先删除旧的关联
        couponDishItemMapper.deleteByCouponId(couponId);
        // 再批量插入新的关联
        if (items != null && !items.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (CouponDishItem item : items) {
                item.setCouponId(couponId);
                item.setCreatedAt(now);
            }
            saveBatch(items);
            log.info("优惠券菜品关联保存成功: couponId={}, itemCount={}", couponId, items.size());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItemsByCouponId(Long couponId) {
        int deleted = couponDishItemMapper.deleteByCouponId(couponId);
        log.info("优惠券菜品关联删除: couponId={}, deletedCount={}", couponId, deleted);
    }
}
