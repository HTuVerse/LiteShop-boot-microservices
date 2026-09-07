package org.hongtu.restaurant.convert;

import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.DAO.*;
import org.hongtu.restaurant.DTO.*;
import org.hongtu.restaurant.VO.RestaurantOrderVO;
import org.hongtu.restaurant.VO.TableSessionVO;
import org.hongtu.restaurant.service.TableSessionService;
import org.hongtu.utils.BeanCopyUtils;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;

import java.util.List;
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

        // 5. 填充会话状态
        fillSessionRelatedInfo(vo,order.getOrderNo());
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
        fillSessionRelatedInfo(vo,order.getOrderNo());

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
        
        return orders.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
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
        
        return orders.stream()
                .map(this::toVOWithoutItems)
                .collect(Collectors.toList());
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
            if (storeInfo != null) {
                vo.setStoreName(storeInfo.getStoreName());
                vo.setAddress(storeInfo.getAddress());
                vo.setPhone(storeInfo.getPhone());
                vo.setBusinessHours(storeInfo.getBusinessHours());
                vo.setPickupMode(storeInfo.getPickupMode());
            }
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
            if (tableInfo != null) {
                vo.setTableName(tableInfo.getTableName());
            }
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
            if (tableSession != null) {
                vo.setSessionStatus(tableSession.getStatus());
            }
        } catch (Exception e) {
            log.error("填充餐桌信息失败，餐桌ID：{}", sessionNo, e);
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

