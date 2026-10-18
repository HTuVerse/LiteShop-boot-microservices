package org.hongtu.restaurant.convert;

import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.DAO.*;
import org.hongtu.restaurant.DTO.*;
import org.hongtu.restaurant.VO.RestaurantOrderVO;
import org.hongtu.utils.BeanCopyUtils;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 餐厅订单转换器
 * 负责将 RestaurantOrder 实体转换为 RestaurantOrderVO 视图对象
 * 
 * @author Ht
 * @since 2025-11-01
 */
@Slf4j
@Component
public class RestaurantOrderConverter {

    @Resource
    private StoreInfoMapper storeInfoMapper;
    
    @Resource
    private TableInfoMapper tableInfoMapper;
    
    @Resource
    private RestaurantOrderItemMapper restaurantOrderItemMapper;
    
    @Resource
    private DishMapper dishMapper;

    @Resource
    private OrderDishChangeLogMapper orderDishChangeLogMapper;

    @Resource
    private TableSessionMapper tableSessionMapper;

    /**
     * 将 RestaurantOrder 转换为 RestaurantOrderVO（包含完整信息）
     * 
     * @param order 订单实体
     * @return 订单VO（包含门店信息、餐桌信息、订单明细）
     */
    public RestaurantOrderVO toVO(RestaurantOrder order) {
        if (order == null) {
            return null;
        }

        // 1. 复制基本属性
        RestaurantOrderVO vo = BeanCopyUtils.copyBean(order, RestaurantOrderVO.class);

        // 2. 填充门店信息
        fillStoreInfo(vo, order.getStoreId());

        // 3. 填充餐桌信息
        fillTableInfo(vo, order.getTableId());

        // 4. 填充订单明细
        fillOrderItems(vo, order.getId());
        vo.setDishChanges(orderDishChangeLogMapper.selectList(new LambdaQueryWrapper<OrderDishChangeLog>()
                .eq(OrderDishChangeLog::getOrderId, order.getId())
                .orderByDesc(OrderDishChangeLog::getOccurredAt)));

        // 5. 填充会话状态
        fillSessionRelatedInfo(vo, order.getSessionNo());
        return vo;
    }

    /**
     * 将 RestaurantOrder 转换为 RestaurantOrderVO（不查询订单明细）
     * 适用于列表查询场景，提高性能
     * 
     * @param order 订单实体
     * @return 订单VO（不包含订单明细）
     */
    public RestaurantOrderVO toVOWithoutItems(RestaurantOrder order) {
        if (order == null) {
            return null;
        }

        // 1. 复制基本属性
        RestaurantOrderVO vo = BeanCopyUtils.copyBean(order, RestaurantOrderVO.class);

        // 2. 填充门店信息
        fillStoreInfo(vo, order.getStoreId());

        // 3. 填充餐桌信息
        fillTableInfo(vo, order.getTableId());

        // 4. 填充会话状态
        fillSessionRelatedInfo(vo, order.getSessionNo());

        return vo;
    }

    /**
     * 批量转换（包含订单明细）
     * 
     * @param orders 订单实体列表
     * @return 订单VO列表
     */
    public List<RestaurantOrderVO> toVOList(List<RestaurantOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        
        return toVOListInternal(orders, true);
    }

    /**
     * 批量转换（不包含订单明细）
     * 适用于列表查询场景
     * 
     * @param orders 订单实体列表
     * @return 订单VO列表（不包含订单明细）
     */
    public List<RestaurantOrderVO> toVOListWithoutItems(List<RestaurantOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        
        return toVOListInternal(orders, false);
    }

    /**
     * 批量加载关联信息，避免订单列表按行查询门店、桌台、明细、菜品和会话。
     */
    private List<RestaurantOrderVO> toVOListInternal(List<RestaurantOrder> orders, boolean includeItems) {
        List<Long> storeIds = orders.stream().map(RestaurantOrder::getStoreId)
                .filter(Objects::nonNull).distinct().toList();
        List<Long> tableIds = orders.stream().map(RestaurantOrder::getTableId)
                .filter(Objects::nonNull).distinct().toList();
        List<String> sessionNos = orders.stream().map(RestaurantOrder::getSessionNo)
                .filter(Objects::nonNull).distinct().toList();

        Map<Long, StoreInfo> stores = storeIds.isEmpty() ? Collections.emptyMap()
                : storeInfoMapper.selectByIds(storeIds).stream()
                .collect(Collectors.toMap(StoreInfo::getId, Function.identity(), (left, right) -> left));
        Map<Long, TableInfo> tables = tableIds.isEmpty() ? Collections.emptyMap()
                : tableInfoMapper.selectByIds(tableIds).stream()
                .collect(Collectors.toMap(TableInfo::getId, Function.identity(), (left, right) -> left));
        Map<String, TableSession> sessions = sessionNos.isEmpty() ? Collections.emptyMap()
                : tableSessionMapper.selectList(new LambdaQueryWrapper<TableSession>()
                        .in(TableSession::getSessionNo, sessionNos)).stream()
                .collect(Collectors.toMap(TableSession::getSessionNo, Function.identity(), (left, right) -> left));

        Map<Long, List<RestaurantOrderItem>> itemsByOrder = Collections.emptyMap();
        Map<Long, Dish> dishes = Collections.emptyMap();
        if (includeItems) {
            List<Long> orderIds = orders.stream().map(RestaurantOrder::getId)
                    .filter(Objects::nonNull).distinct().toList();
            List<RestaurantOrderItem> items = orderIds.isEmpty() ? List.of()
                    : restaurantOrderItemMapper.selectList(new LambdaQueryWrapper<RestaurantOrderItem>()
                    .in(RestaurantOrderItem::getOrderId, orderIds));
            itemsByOrder = items.stream().collect(Collectors.groupingBy(RestaurantOrderItem::getOrderId));
            List<Long> dishIds = items.stream().map(RestaurantOrderItem::getDishId)
                    .filter(Objects::nonNull).distinct().toList();
            dishes = dishIds.isEmpty() ? Collections.emptyMap()
                    : dishMapper.selectByIds(dishIds).stream()
                    .collect(Collectors.toMap(Dish::getId, Function.identity(), (left, right) -> left));
        }

        Map<Long, List<RestaurantOrderItem>> finalItemsByOrder = itemsByOrder;
        Map<Long, Dish> finalDishes = dishes;
        return orders.stream().map(order -> {
            RestaurantOrderVO vo = BeanCopyUtils.copyBean(order, RestaurantOrderVO.class);
            applyStoreInfo(vo, stores.get(order.getStoreId()));
            applyTableInfo(vo, tables.get(order.getTableId()));
            applySessionInfo(vo, sessions.get(order.getSessionNo()));
            if (includeItems) {
                vo.setItems(finalItemsByOrder.getOrDefault(order.getId(), List.of()).stream()
                        .map(item -> toOrderItemVO(item, finalDishes.get(item.getDishId())))
                        .toList());
            }
            return vo;
        }).toList();
    }

    private void applyStoreInfo(RestaurantOrderVO vo, StoreInfo storeInfo) {
        if (storeInfo == null) {
            return;
        }
        vo.setStoreName(storeInfo.getStoreName());
        vo.setAddress(storeInfo.getAddress());
        vo.setPhone(storeInfo.getPhone());
        vo.setBusinessHours(storeInfo.getBusinessHours());
        vo.setPickupMode(storeInfo.getPickupMode());
    }

    private void applyTableInfo(RestaurantOrderVO vo, TableInfo tableInfo) {
        if (tableInfo != null) {
            vo.setTableName(tableInfo.getTableName());
        }
    }

    private void applySessionInfo(RestaurantOrderVO vo, TableSession tableSession) {
        if (tableSession != null) {
            vo.setSessionStatus(tableSession.getStatus());
        }
    }

    /**
     * 填充门店信息
     * 
     * @param vo 订单VO
     * @param storeId 门店ID
     */
    private void fillStoreInfo(RestaurantOrderVO vo, Long storeId) {
        if (storeId == null) {
            return;
        }

        try {
            StoreInfo storeInfo = storeInfoMapper.selectById(storeId);
            applyStoreInfo(vo, storeInfo);
        } catch (Exception e) {
            log.error("填充门店信息失败，门店ID：{}", storeId, e);
        }
    }

    /**
     * 填充餐桌信息
     * 
     * @param vo 订单VO
     * @param tableId 餐桌ID
     */
    private void fillTableInfo(RestaurantOrderVO vo, Long tableId) {
        if (tableId == null) {
            return;
        }

        try {
            TableInfo tableInfo = tableInfoMapper.selectById(tableId);
            applyTableInfo(vo, tableInfo);
        } catch (Exception e) {
            log.error("填充餐桌信息失败，餐桌ID：{}", tableId, e);
        }
    }

    /**
     * 填充订单明细
     * 
     * @param vo 订单VO
     * @param orderId 订单ID
     */
    private void fillOrderItems(RestaurantOrderVO vo, Long orderId) {
        if (orderId == null) {
            return;
        }

        try {
            // 查询订单明细
            LambdaQueryWrapper<RestaurantOrderItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(RestaurantOrderItem::getOrderId, orderId);
            List<RestaurantOrderItem> items = restaurantOrderItemMapper.selectList(itemWrapper);

            // 转换为VO并填充菜品信息
            List<RestaurantOrderVO.OrderItemVO> itemVOs = items.stream()
                    .map(this::toOrderItemVO)
                    .collect(Collectors.toList());

            vo.setItems(itemVOs);
        } catch (Exception e) {
            log.error("填充订单明细失败，订单ID：{}", orderId, e);
        }
    }

    /**
     * 填充会话状态
     * */
    public void fillSessionRelatedInfo(RestaurantOrderVO vo, String sessionNo) {
        if (sessionNo == null) {
            return;
        }

        try {
            TableSession tableSession = tableSessionMapper.selectBySessionNo(sessionNo);
            applySessionInfo(vo, tableSession);
        } catch (Exception e) {
            log.error("填充会话信息失败，会话编号：{}", sessionNo, e);
        }
    }

    /**
     * 将 RestaurantOrderItem 转换为 OrderItemVO
     * 
     * @param item 订单明细实体
     * @return 订单明细VO
     */
    private RestaurantOrderVO.OrderItemVO toOrderItemVO(RestaurantOrderItem item) {
        RestaurantOrderVO.OrderItemVO itemVO = BeanCopyUtils.copyBean(item, RestaurantOrderVO.OrderItemVO.class);

        // 查询并填充菜品信息
        if (item.getDishId() != null) {
            try {
                Dish dish = dishMapper.selectById(item.getDishId());
                if (dish != null) {
                    itemVO.setDishName(dish.getDishName());
                    itemVO.setMainPic(dish.getDishPics());
                }
            } catch (Exception e) {
                log.error("查询菜品信息失败，菜品ID：{}", item.getDishId(), e);
            }
        }

        return itemVO;
    }

    private RestaurantOrderVO.OrderItemVO toOrderItemVO(RestaurantOrderItem item, Dish dish) {
        RestaurantOrderVO.OrderItemVO itemVO = BeanCopyUtils.copyBean(item, RestaurantOrderVO.OrderItemVO.class);
        if (dish != null) {
            itemVO.setDishName(dish.getDishName());
            itemVO.setMainPic(dish.getDishPics());
        }
        return itemVO;
    }

    /**
     * 手动填充订单明细（用于已有VO对象的场景）
     * 
     * @param vo 订单VO
     */
    public void fillOrderItems(RestaurantOrderVO vo) {
        if (vo == null || vo.getId() == null) {
            return;
        }
        fillOrderItems(vo, vo.getId());
    }

    /**
     * 手动填充门店和餐桌信息（用于已有VO对象的场景）
     * 
     * @param vo 订单VO
     */
    public void fillStoreAndTableInfo(RestaurantOrderVO vo) {
        if (vo == null) {
            return;
        }
        fillStoreInfo(vo, vo.getStoreId());
        fillTableInfo(vo, vo.getTableId());
    }
}

