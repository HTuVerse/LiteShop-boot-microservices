package org.hongtu.mall.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import org.hongtu.DTO.Coupon;
import org.hongtu.api.client.CouponFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.enums.OnOrOffShelfEnum;
import org.hongtu.exception.BusinessException;
import org.hongtu.mall.dto.MallProduct;
import org.hongtu.mall.dto.MallProductRequest;
import org.hongtu.tenant.TenantContext;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 兑换商品目录，所有查询和写入均明确限定租户。 */
@Service
public class MallCatalogService {
    @Resource private JdbcTemplate jdbc;
    @Resource private CouponFeignClient couponClient;
    private static final BeanPropertyRowMapper<MallProduct> ROW = new BeanPropertyRowMapper<>(MallProduct.class);

    /** 分页列出租户商品。 */
    public Map<String, Object> page(int currentPage, int pageSize, String name, boolean publishedOnly) {
        long tenantId = TenantContext.getTenantId();
        int page = Math.max(1, currentPage);
        int size = Math.max(1, Math.min(100, pageSize));
        String keyword = "%" + (name == null ? "" : name.trim()) + "%";
        String filter = publishedOnly ? " AND status = 1 AND stock > 0" : "";
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM mall_product WHERE tenant_id = ? AND name LIKE ?" + filter,
                Long.class, tenantId, keyword);
        List<MallProduct> records = jdbc.query("SELECT * FROM mall_product WHERE tenant_id = ? AND name LIKE ?" + filter
                        + " ORDER BY sort DESC, created_at DESC LIMIT ? OFFSET ?", ROW, tenantId, keyword, size, (page - 1) * size);
        return Map.of("records", records, "total", total == null ? 0 : total, "current", page, "size", size);
    }

    /** 查询本租户商品。 */
    public MallProduct get(Long id) {
        if (id == null) throw new BusinessException("商品ID不能为空");
        List<MallProduct> rows = jdbc.query("SELECT * FROM mall_product WHERE id = ? AND tenant_id = ?", ROW,
                id, TenantContext.getTenantId());
        if (rows.isEmpty()) throw new BusinessException("兑换商品不存在");
        return rows.getFirst();
    }

    /** 新建或编辑商品，关联券必须属于当前租户且可发放。 */
    public MallProduct save(Long id, MallProductRequest req) {
        validate(req);
        long tenantId = TenantContext.getTenantId();
        if (id == null) {
            id = IdWorker.getId();
            jdbc.update("INSERT INTO mall_product (id,tenant_id,name,description,image_url,coupon_id,points_price,pricing_mode,cash_price,stock,status,sort,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW())",
                    id, tenantId, req.getName().trim(), req.getDescription(), req.getImageUrl(), req.getCouponId(),
                    req.getPointsPrice(), req.getPricingMode(), req.getCashPrice(), req.getStock(), req.getStatus(), req.getSort());
        } else {
            MallProduct old = get(id);
            if (old.getStock() < 0) throw new BusinessException("商品库存异常");
            // 编辑时比较读取的库存，避免覆盖并发兑换。
            int changed = jdbc.update("UPDATE mall_product SET name=?, description=?, image_url=?, coupon_id=?, points_price=?, pricing_mode=?, cash_price=?, stock=?, status=?, sort=?, updated_at=NOW() WHERE id=? AND tenant_id=? AND stock=?",
                    req.getName().trim(), req.getDescription(), req.getImageUrl(), req.getCouponId(), req.getPointsPrice(),
                    req.getPricingMode(), req.getCashPrice(), req.getStock(), req.getStatus(), req.getSort(), id, tenantId, old.getStock());
            if (changed != 1) throw new BusinessException("商品更新失败，请刷新后重试");
        }
        return get(id);
    }

    /** 有兑换历史的商品只能下架，保留历史快照。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        get(id);
        // 与兑换扣库存串行化，避免查无订单后删除正在兑换的商品。
        jdbc.queryForObject("SELECT id FROM mall_product WHERE id=? AND tenant_id=? FOR UPDATE", Long.class,
                id, TenantContext.getTenantId());
        Long orders = jdbc.queryForObject("SELECT COUNT(*) FROM mall_exchange_order WHERE tenant_id=? AND product_id=?", Long.class,
                TenantContext.getTenantId(), id);
        if (orders != null && orders > 0) {
            jdbc.update("UPDATE mall_product SET status=0,updated_at=NOW() WHERE id=? AND tenant_id=?", id, TenantContext.getTenantId());
        } else {
            jdbc.update("DELETE FROM mall_product WHERE id=? AND tenant_id=?", id, TenantContext.getTenantId());
        }
    }

    private void validate(MallProductRequest req) {
        if (req == null || req.getName() == null || req.getName().isBlank() || req.getName().length() > 80
                || req.getCouponId() == null || req.getPointsPrice() == null || req.getPointsPrice() < 0
                || req.getCashPrice() == null || req.getCashPrice().scale() > 2
                || req.getCashPrice().signum() < 0 || req.getCashPrice().compareTo(new java.math.BigDecimal("99999999.99")) > 0
                || req.getStock() == null || req.getStock() < 0 || req.getStatus() == null
                || (req.getStatus() != 0 && req.getStatus() != 1)) throw new BusinessException("商品参数无效");
        if (req.getDescription() != null && req.getDescription().length() > 500) throw new BusinessException("商品描述过长");
        if (req.getImageUrl() != null && req.getImageUrl().length() > 500) throw new BusinessException("图片地址过长");
        boolean points = req.getPointsPrice() > 0;
        boolean cash = req.getCashPrice().signum() > 0;
        String expected = points && cash ? "POINTS_CASH" : points ? "POINTS" : cash ? "CASH" : "";
        if (!expected.equals(req.getPricingMode())) throw new BusinessException("积分与现金价格不符合定价方式");
        if (req.getSort() == null) req.setSort(0);
        Coupon coupon = FeignResultUtil.unwrap(couponClient.getCouponById(req.getCouponId()));
        if (coupon == null || coupon.getStatus() != OnOrOffShelfEnum.ON_SHELF || coupon.getRecallTime() != null
                || (coupon.getEndTime() != null && !coupon.getEndTime().isAfter(LocalDateTime.now())))
            throw new BusinessException("关联优惠券不存在、已下架或已过期");
    }
}
