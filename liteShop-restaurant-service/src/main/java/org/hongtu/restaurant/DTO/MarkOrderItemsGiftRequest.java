package org.hongtu.restaurant.DTO;

import lombok.Data;

import java.util.List;

/**
 * 标记订单项为赠菜/取消赠菜 请求
 * 用于桌位模式下，对已存在的订单项进行赠菜标记（不改变单价，优惠金额=该行小计）
 *
 * @author Ht
 */
@Data
public class MarkOrderItemsGiftRequest {

    /** 订单ID */
    private Long orderId;

    /** 会话编号（可选，传则校验订单属于该会话） */
    private String sessionNo;

    /** 要标记的订单项ID列表 */
    private List<Long> orderItemIds;

    /** 是否赠菜：1-设为赠菜，0-取消赠菜 */
    private Integer isGift;
}
