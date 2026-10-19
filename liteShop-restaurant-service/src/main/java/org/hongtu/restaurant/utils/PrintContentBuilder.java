package org.hongtu.restaurant.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.StatusEnum;
import org.hongtu.restaurant.DTO.PrintTemplate;
import org.hongtu.restaurant.VO.RestaurantOrderVO;
import org.hongtu.restaurant.enums.pickupModeEnum;
import org.hongtu.restaurant.enums.PaymentModeEnum;
import org.hongtu.restaurant.enums.PaymentStatusEnum;
import org.hongtu.restaurant.enums.SceneTypeEnum;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 打印内容构建器
 * 根据打印模板配置动态生成小票内容
 *
 * @author hongtu
 */
@Slf4j
public class PrintContentBuilder {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 根据模板和订单数据构建打印内容
     * 支持普通模式和取餐码突出模式，通过 template.showPickupCode 控制
     *
     * @param template 打印模板配置
     * @param orderVO 订单VO对象
     * @return 格式化的打印内容
     */
    public static String buildOrderContent(PrintTemplate template, RestaurantOrderVO orderVO) {
        // 判断是否启用取餐码突出显示模式
        boolean isPickupCodeFocused = template.getShowPickupCode() != null &&
                template.getShowPickupCode() == BooleanEnum.YES && orderVO.getPickupMode()== pickupModeEnum.NUMBERCALLING;

        return buildNormalContent(template, orderVO, isPickupCodeFocused);
    }

    /** 使用演示订单生成与真实订单相同格式的小票指令，供在线预览和测试打印。 */
    public static String buildSampleContent(PrintTemplate template) {
        return buildSampleContent(template, template.getStoreId());
    }

    /** 试打全局模板时使用所选打印机的门店 ID 替换演示订单变量。 */
    public static String buildSampleContent(PrintTemplate template, Long storeId) {
        RestaurantOrderVO order = new RestaurantOrderVO();
        order.setOrderNo("DEMO-001");
        order.setStoreId(storeId == null ? 1L : storeId);
        order.setStoreName("示例门店");
        order.setAddress("示例路 88 号");
        order.setPhone("010-12345678");
        order.setBusinessHours("10:00-22:00");
        order.setOrderType(SceneTypeEnum.RESTAURANT);
        order.setPickupMode(pickupModeEnum.NUMBERCALLING);
        order.setPickupCode("A123");
        order.setTableId(1L);
        order.setTableName("A01");
        order.setDiningCount(2);
        order.setCreatedAt(LocalDateTime.now());
        order.setPaymentMode(PaymentModeEnum.OFFLINE);
        order.setPaymentStatus(PaymentStatusEnum.PAID);
        order.setPaymentTime(LocalDateTime.now());
        order.setNote("少油少盐");
        order.setServiceFee(BigDecimal.ZERO);
        order.setDiscountAmount(new BigDecimal("8.00"));
        order.setTotalAmount(new BigDecimal("80.00"));
        RestaurantOrderVO.OrderItemVO item = new RestaurantOrderVO.OrderItemVO();
        item.setDishName("示例菜品");
        item.setUnitSellingPrice(new BigDecimal("44.00"));
        item.setQuantity(2);
        item.setLinePayableAmount(new BigDecimal("80.00"));
        item.setDiscountAmount(new BigDecimal("8.00"));
        item.setSpecOptionsText("微辣");
        item.setNote("不要花生");
        order.setItems(List.of(item));
        return buildOrderContent(template, order);
    }

    /**
     * 构建小票内容，取餐码模式只增加突出区块，其余配置规则保持一致。
     *
     * @param template 打印模板配置
     * @param orderVO 订单VO对象
     * @param pickupCodeFocused 是否突出取餐码
     * @return 格式化的打印内容
     */
    private static String buildNormalContent(PrintTemplate template, RestaurantOrderVO orderVO,
                                             boolean pickupCodeFocused) {
        StringBuilder content = new StringBuilder();

        // 纸张宽度（用于计算列宽）
        int paperWidth = template.getPaperWidth() == null ? 32 : template.getPaperWidth().getCharactersPerLine();

        // ============ 顶部Logo ============
        if (template.getShowLogo() != null && template.getShowLogo() == StatusEnum.getByCode(1) &&
                template.getLogoUrl() != null && !template.getLogoUrl().isEmpty()) {
            // 打印Logo图片（飞鹅云支持图片打印）
            content.append("<IMG>").append(template.getLogoUrl()).append("</IMG><BR>");
            content.append("<BR>");
        }

        // ============ 店铺名称 ============
        String storeName = orderVO.getStoreName();
        Integer storeNameStyle = template.getStoreNameStyle() != null ? template.getStoreNameStyle() : 2;

        switch (storeNameStyle) {
            case 0: // 普通
                content.append(storeName).append("<BR>");
                break;
            case 1: // 加粗
                content.append("<B>").append(storeName).append("</B>");
                break;
            case 2: // 加粗+居中
                content.append("<CB>").append(storeName).append("</CB>");
                break;
            case 3: // 加粗+居中+放大
                content.append("<CB><L>").append(storeName).append("</L></CB>");
                break;
        }
        content.append("<BR>");

        // 取餐码仅增加突出区块，其余板块仍按同一模板规则打印。
        if (pickupCodeFocused) {
            content.append(buildDividerLine(paperWidth));
            content.append("<CB><L>您的取餐码</L></CB><BR>");
            String pickupCode = orderVO.getPickupCode();
            content.append("<CB><L><L><B>")
                    .append(pickupCode == null || pickupCode.isBlank() ? "暂无" : pickupCode)
                    .append("</B></L></L></CB><BR>");
            content.append("<C>(等餐期间请注意听取叫号)</C><BR>");
        }

        // ============ 营业时间 ============
        if (template.getShowBusinessHours() != null && template.getShowBusinessHours() == StatusEnum.getByCode(1) &&
                orderVO.getBusinessHours() != null && !orderVO.getBusinessHours().isEmpty()) {
            content.append("<C>营业时间：").append(orderVO.getBusinessHours()).append("</C><BR>");
            content.append("<BR>");
        }

        // ============ 订单信息 ============
        content.append(buildDividerLine(paperWidth));

        // 解析订单信息显示字段
        List<String> orderFields = parseOrderInfoFields(template.getOrderInfoFields());
        for (String field : orderFields) {
            switch (field) {
                case "orderNo":
                    content.append("订单号：").append(orderVO.getOrderNo()).append("<BR>");
                    break;
                case "tableName":
                    if (orderVO.getTableName() != null) {
                        content.append("桌　号：").append(orderVO.getTableName()).append("<BR>");
                    }
                    break;
                case "orderType":
                    if (orderVO.getOrderType() != null) {
                        content.append("类　型：").append(orderVO.getOrderType().getDesc()).append("<BR>");
                    }
                    break;
                case "diningCount":
                    if (orderVO.getDiningCount() != null) {
                        content.append("就餐人数：").append(orderVO.getDiningCount()).append("人<BR>");
                    }
                    break;
                case "pickupMode":
                    if (orderVO.getPickupMode() != null) {
                        content.append("取餐方式：").append(orderVO.getPickupMode().getDesc()).append("<BR>");
                    }
                    break;
                case "pickupCode":
                    if (orderVO.getPickupCode() != null && !orderVO.getPickupCode().isEmpty()) {
                        content.append("取餐码：").append(orderVO.getPickupCode()).append("<BR>");
                    }
                    break;
                case "createdAt":
                    if (orderVO.getCreatedAt() != null) {
                        content.append("下单时间：").append(orderVO.getCreatedAt().format(DATE_FORMATTER)).append("<BR>");
                    }
                    break;
            }
        }
        // ============ 顶部地址和电话 ============
        if (template.getShowAddress() != null && template.getShowAddress() == StatusEnum.getByCode(1)) {
            content.append("商家地址：").append(orderVO.getAddress()).append("<BR>");
        }
        if (template.getShowPhone() != null && template.getShowPhone() == StatusEnum.getByCode(1)) {
            content.append("商家电话：").append(orderVO.getPhone()).append("<BR>");
        }
        content.append(buildDividerLine(paperWidth));

        // ============ 菜品列表 ============
        Integer dishListStyle = template.getDishListStyle() != null ? template.getDishListStyle() : 0;

        // 表头
        if (paperWidth == 48) { // 80mm
            content.append("菜品名称          单价   数量        金额<BR>");
        } else { // 58mm
            content.append("菜品       单价  数量     金额<BR>");
        }
        content.append(buildDashLine(paperWidth));

        // 菜品明细
        List<RestaurantOrderVO.OrderItemVO> items = orderVO.getItems();

        for (RestaurantOrderVO.OrderItemVO item : items) {
            // 菜品名称长度
            int dishNameWidth = paperWidth == 48 ? 18 : 11;

            String dishName = item.getDishName();
            BigDecimal price = item.getUnitSellingPrice();
            Integer quantity = item.getQuantity();
            BigDecimal itemTotal = item.getLinePayableAmount();
            BigDecimal discountPrice = item.getDiscountAmount();
            Integer isGift = item.getIsGift();

            // 菜名：赠菜在名称右侧加「赠」
            String formattedDishName = formatDishNameWithGift(dishName, dishNameWidth, isGift);
            String priceStr = formatPrice(price);
            String quantityStr = String.valueOf(quantity);
            String totalStr = formatAmountWithOriginal(itemTotal, discountPrice, 12);
            int amountColWidth = (totalStr.contains("→") ? 12 : 9);

            // 第一行：菜名 + 单价 + 数量 + 金额（有优惠时金额为「原价→实付」）
            content.append(formattedDishName);
            if (paperWidth == 48) {
                content.append(formatRightAlign(priceStr, 6));
                content.append(formatCenter(quantityStr, 6));
                content.append(formatRightAlign(totalStr, amountColWidth));
            } else {
                content.append(formatRightAlign(priceStr, 5));
                content.append(formatCenter(quantityStr, 5));
                content.append(" ").append(formatRightAlign(totalStr, amountColWidth));
            }
            content.append("<BR>");

            // 显示规格信息
            if (dishListStyle == 1 && template.getShowDishSpec() == StatusEnum.getByCode(1) &&
                    item.getSpecOptionsText() != null && !item.getSpecOptionsText().isEmpty() &&
                    !"null".equals(item.getSpecOptionsText())) {
                String[] specs = item.getSpecOptionsText().split("、");
                for (String spec : specs) {
                    if (spec != null && !spec.trim().isEmpty()) {
                        content.append("  [").append(spec.trim()).append("]<BR>");
                    }
                }
            }

            // 显示备注
            if (dishListStyle == 1 && template.getShowDishNote() == StatusEnum.getByCode(1) &&
                    item.getNote() != null && !item.getNote().isEmpty() && !"null".equals(item.getNote())) {
                content.append("  备注：").append(item.getNote()).append("<BR>");
            }
        }

        // ============ 合计部分 ============
        content.append(buildDashLine(paperWidth));

        // 服务费
        if (orderVO.getServiceFee() != null && orderVO.getServiceFee().compareTo(BigDecimal.ZERO) > 0) {
            content.append("<RIGHT>服务费：").append(formatPrice(orderVO.getServiceFee())).append("元</RIGHT><BR>");
        }

        // 优惠金额
        if (orderVO.getDiscountAmount() != null && orderVO.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            content.append("<RIGHT>优惠：-").append(formatPrice(orderVO.getDiscountAmount())).append("元</RIGHT><BR>");
        }

        // 总计
        content.append("<RIGHT><B>总计：").append(formatPrice(orderVO.getTotalAmount())).append("元</B></RIGHT><BR>");
        content.append(buildDividerLine(paperWidth));

        // ============ 订单备注 ============
        if (template.getShowOrderNote() != null && template.getShowOrderNote() == StatusEnum.getByCode(1) &&
                orderVO.getNote() != null && !orderVO.getNote().isEmpty() && !"null".equals(orderVO.getNote())) {
            content.append("订单备注：").append(orderVO.getNote()).append("<BR>");
        }

        // ============ 支付信息 ============
        if (template.getShowPaymentInfo() != null && template.getShowPaymentInfo() == StatusEnum.getByCode(1)) {
            if (orderVO.getPaymentMode() != null) {
                content.append("支付方式：").append(orderVO.getPaymentMode().getDesc()).append("<BR>");
            }
            if (orderVO.getPaymentStatus() != null) {
                content.append("支付状态：").append(orderVO.getPaymentStatus().getDesc()).append("<BR>");
            }
            if (orderVO.getPaymentTime() != null) {
                content.append("支付时间：").append(orderVO.getPaymentTime().format(DATE_FORMATTER)).append("<BR>");
            }
            content.append("<BR>");
        }

        // ============ 底部欢迎语 ============
        if (template.getFooterText() != null && !template.getFooterText().isEmpty()) {
            content.append("<C>").append(template.getFooterText()).append("</C><BR>");
        }

        // ============ 二维码 ============
        if (template.getShowQrcode() != null && template.getShowQrcode() == StatusEnum.getByCode(1) &&
                template.getQrcodeTemplate() != null && !template.getQrcodeTemplate().isEmpty()) {
            String qrcodeContent = template.getQrcodeTemplate()
                    .replace("{orderNo}", orderVO.getOrderNo())
                    .replace("{storeId}", String.valueOf(orderVO.getStoreId()))
                    .replace("{tableId}", orderVO.getTableId() != null ? String.valueOf(orderVO.getTableId()) : "");

            content.append("<QR>").append(qrcodeContent).append("</QR>");
            content.append("<BR>");
        }

        // 底部留白
        content.append("<BR>");
        content.append("<BR>");

        return content.toString();
    }


    /**
     * 构建分隔线（实线）
     */
    private static String buildDividerLine(int width) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < width; i++) {
            line.append("=");
        }
        return line.append("<BR>").toString();
    }

    /**
     * 构建分隔线（虚线）
     */
    private static String buildDashLine(int width) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < width; i++) {
            line.append("-");
        }
        return line.append("<BR>").toString();
    }

    /**
     * 解析订单信息显示字段
     */
    private static List<String> parseOrderInfoFields(String orderInfoFields) {
        if (orderInfoFields == null || orderInfoFields.isEmpty()) {
            // 默认显示字段
            return List.of("orderNo", "tableName", "orderType", "createdAt");
        }
        try {
            JSONArray array = JSON.parseArray(orderInfoFields);
            return array.toJavaList(String.class);
        } catch (Exception e) {
            log.error("解析订单信息字段失败", e);
            return List.of("orderNo", "tableName", "orderType", "createdAt");
        }
    }

    /**
     * 格式化文本，确保占用指定字节数
     * 汉字占2个字节，英文占1个字节
     */
    private static String formatText(String text, int maxBytes) {
        try {
            int bytes = text.getBytes("GBK").length;

            // 如果超长，截取
            if (bytes > maxBytes) {
                int len = 0;
                int count = 0;
                for (char c : text.toCharArray()) {
                    len += String.valueOf(c).getBytes("GBK").length;
                    if (len > maxBytes - 2) {  // 预留2个字节显示".."
                        break;
                    }
                    count++;
                }
                text = text.substring(0, count) + "..";
                bytes = text.getBytes("GBK").length;
            }

            // 右侧补空格
            StringBuilder result = new StringBuilder(text);
            for (int i = bytes; i < maxBytes; i++) {
                result.append(" ");
            }
            return result.toString();

        } catch (UnsupportedEncodingException e) {
            return text;
        }
    }

    /**
     * 格式化价格（保留2位小数）
     */
    private static String formatPrice(BigDecimal price) {
        if (price == null) {
            return "0.00";
        }
        return price.setScale(2, RoundingMode.HALF_UP).toString();
    }

    /**
     * 菜品名称：若为赠菜则在名称右侧加「赠」
     */
    private static String formatDishNameWithGift(String dishName, int nameWidth, Integer isGift) {
        if (dishName == null) {
            dishName = "";
        }
        if (isGift != null && isGift == 1) {
            int w = Math.max(1, nameWidth - 1);
            return formatText(dishName, w) + "赠";
        }
        return formatText(dishName, nameWidth);
    }

    /**
     * 金额列：有优惠时显示原价（划线价）+ 实付 totalPrice，格式为「原价x.xx 实付y.yy」；无优惠仅显示 totalPrice
     */
    private static String formatAmountWithOriginal(BigDecimal totalPrice, BigDecimal discountPrice, int amountWidth) {
        String actualStr = formatPrice(totalPrice);
        if (discountPrice == null || discountPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return actualStr;
        }
        BigDecimal originalTotal = (totalPrice != null ? totalPrice : BigDecimal.ZERO).add(discountPrice);
        String originalStr = formatPrice(originalTotal);
        String combined = originalStr + "→" + actualStr;
        if (combined.length() <= amountWidth) {
            return combined;
        }
        return originalStr + " " + actualStr;
    }

    /**
     * 右对齐格式化
     */
    private static String formatRightAlign(String text, int width) {
        int len = text.length();
        if (len >= width) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < width - len; i++) {
            result.append(" ");
        }
        result.append(text);
        return result.toString();
    }

    /**
     * 居中对齐格式化
     */
    private static String formatCenter(String text, int width) {
        int len = text.length();
        if (len >= width) {
            return text;
        }

        int leftPad = (width - len) / 2;
        int rightPad = width - len - leftPad;

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < leftPad; i++) {
            result.append(" ");
        }
        result.append(text);
        for (int i = 0; i < rightPad; i++) {
            result.append(" ");
        }
        return result.toString();
    }
}
