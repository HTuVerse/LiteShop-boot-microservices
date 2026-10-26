package org.hongtu.restaurant.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.restaurant.DAO.RestaurantOrderMapper;
import org.hongtu.restaurant.DTO.RestaurantOrder;
import org.hongtu.restaurant.VO.RestaurantOrderVO;
import org.hongtu.restaurant.convert.RestaurantOrderConverter;
import org.hongtu.restaurant.enums.OrderStatusEnum;
import org.hongtu.restaurant.enums.PaymentStatusEnum;
import org.hongtu.restaurant.enums.SceneTypeEnum;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 订单只读分页查询，集中处理筛选条件和订单展示转换。 */
@Service
@RequiredArgsConstructor
public class RestaurantOrderQueryService {
    private final RestaurantOrderMapper orders;
    private final RestaurantOrderConverter converter;
    private final UserFeignClient users;

    public Page<RestaurantOrderVO> page(Integer currentPage, Integer pageSize,
                                        Long storeId, Long tableId, Long userId,
                                        Integer orderType, Integer status,
                                        Integer paymentStatus, String phoneNumber) {
        // 手机号筛选无匹配时返回空页，不能退化为无筛选的全量列表。
        if (StrUtil.isNotBlank(phoneNumber)) {
            Long matched = FeignResultUtil.unwrap(users.findUserIdByPhone(phoneNumber.trim()));
            if (matched == null || userId != null && !userId.equals(matched))
                return new Page<>(currentPage, pageSize);
            userId = matched;
        }
        LambdaQueryWrapper<RestaurantOrder> filter = new LambdaQueryWrapper<RestaurantOrder>()
                .eq(storeId != null, RestaurantOrder::getStoreId, storeId)
                .eq(tableId != null, RestaurantOrder::getTableId, tableId)
                .eq(userId != null, RestaurantOrder::getUserId, userId)
                .eq(orderType != null, RestaurantOrder::getOrderType, SceneTypeEnum.getByCode(orderType))
                .eq(status != null, RestaurantOrder::getStatus, OrderStatusEnum.getByCode(status))
                .eq(paymentStatus != null, RestaurantOrder::getPaymentStatus, PaymentStatusEnum.getByCode(paymentStatus))
                .orderByDesc(RestaurantOrder::getCreatedAt);
        Page<RestaurantOrder> source = new Page<>(currentPage, pageSize);
        orders.selectPage(source, filter);
        return convert(source);
    }

    /** 顾客可查看自己的近一个月订单，以及当前桌台会话中的共享订单。 */
    public Page<RestaurantOrderVO> pageForUser(Long userId, String sessionNo, Integer sceneType,
                                               Integer currentPage, Integer pageSize) {
        LambdaQueryWrapper<RestaurantOrder> filter = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(sessionNo)) {
            filter.and(w -> w.eq(RestaurantOrder::getUserId, userId)
                    .or().eq(RestaurantOrder::getSessionNo, sessionNo)
                    .ge(RestaurantOrder::getCreatedAt, LocalDateTime.now().minusMonths(1)));
        } else {
            filter.eq(RestaurantOrder::getUserId, userId)
                    .ge(RestaurantOrder::getCreatedAt, LocalDateTime.now().minusMonths(1));
        }
        if (sceneType != null) {
            if (sceneType == 2) {
                filter.eq(RestaurantOrder::getOrderType, SceneTypeEnum.getByCode(2));
            } else if (sceneType == 0 || sceneType == 1) {
                filter.in(RestaurantOrder::getOrderType,
                        SceneTypeEnum.getByCode(0), SceneTypeEnum.getByCode(1));
            }
        }
        filter.orderByDesc(RestaurantOrder::getCreatedAt);
        Page<RestaurantOrder> source = new Page<>(currentPage, pageSize);
        orders.selectPage(source, filter);
        return convert(source);
    }

    private Page<RestaurantOrderVO> convert(Page<RestaurantOrder> source) {
        Page<RestaurantOrderVO> page = new Page<>(source.getCurrent(), source.getSize());
        page.setTotal(source.getTotal());
        page.setRecords(converter.toVOList(source.getRecords()));
        return page;
    }
}
