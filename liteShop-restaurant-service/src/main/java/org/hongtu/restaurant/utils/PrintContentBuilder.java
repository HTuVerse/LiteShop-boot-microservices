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
import org.hongtu.restaurant.enums.PaperWidthEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
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
    private static final Charset PRINT_CHARSET = Charset.forName("GBK");

    /**
     * 根据模板和订单数据构建打印内容
     * 支持普通模式和取餐码突出模式，通过 template.showPickupCode 控制
     *
     * @param template 打印模板配置
     * @param orderVO 订单VO对象
     * @return 格式化的打印内容
     */
    public static String buildOrderContent(PrintTemplate template, RestaurantOrderVO orderVO) {
        return buildOrderContent(template, orderVO, template.getPrintScene(), template.getPaperWidth());
    }

    /** 正式打印按设备实际纸宽排版，避免把 80mm 模板的行宽发给 58mm 打印机。 */
    public static String buildOrderContent(PrintTemplate template, RestaurantOrderVO orderVO,
                                           PaperWidthEnum actualPaperWidth) {
        return buildOrderContent(template, orderVO, template.getPrintScene(), actualPaperWidth);
    }

    /** 加菜单只展示本批菜品与本批金额。 */
    public static String buildOrderContent(PrintTemplate template, RestaurantOrderVO orderVO,
                                           String printScene, PaperWidthEnum actualPaperWidth) {
        return buildOrderContent(template, orderVO, printScene, actualPaperWidth, null);
    }

    /** 正式小票可覆盖模板二维码，供设备扫码完成出餐。 */
    public static String buildOrderContent(PrintTemplate template, RestaurantOrderVO orderVO,
                                           String printScene, PaperWidthEnum actualPaperWidth, String scanCode) {
        // 判断是否启用取餐码突出显示模式
        boolean isPickupCodeFocused = template.getShowPickupCode() != null &&
                template.getShowPickupCode() == BooleanEnum.YES && orderVO.getPickupMode()== pickupModeEnum.NUMBERCALLING;

        return buildNormalContent(template, orderVO, isPickupCodeFocused, actualPaperWidth,
                "ADD_ITEMS".equals(printScene), scanCode);
    }

    /** 使用演示订单生成与真实订单相同格式的小票指令，供在线预览和测试打印。 */
    public static String buildSampleContent(PrintTemplate template) {
        return buildSampleContent(template, template.getStoreId());
    }

    /** 试打全局模板时使用所选打印机的门店 ID 替换演示订单变量。 */
    public static String buildSampleContent(PrintTemplate template, Long storeId) {
        return buildSampleContent(template, storeId, template.getPaperWidth());
    }

    /** 试打按所选打印机纸宽生成，订单数据仍使用演示值。 */
    public static String buildSampleContent(PrintTemplate template, Long storeId,
                                            PaperWidthEnum actualPaperWidth) {
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
        return buildOrderContent(template, order, template.getPrintScene(), actualPaperWidth);
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
                                             boolean pickupCodeFocused, PaperWidthEnum actualPaperWidth,
                                             boolean addItems, String scanCode) {
        StringBuilder content = new StringBuilder();

        // 纸张宽度（用于计算列宽）
        int paperWidth = actualPaperWidth == null ? PaperWidthEnum.MM_58.getCharactersPerLine()
                : actualPaperWidth.getCharactersPerLine();

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
        // 飞鹅 <CB> 是双倍宽居中，长店名在窄纸上改为普通居中加粗。
        if (storeNameStyle == 3 && textWidth(storeName) * 2 > paperWidth) {
            storeNameStyle = 2;
        }

        switch (storeNameStyle) {
            case 0: // 普通
                content.append(storeName);
                break;
            case 1: // 加粗
                content.append("<BOLD>").append(storeName).append("</BOLD>");
                break;
            case 2: // 加粗+居中
                content.append("<C><BOLD>").append(storeName).append("</BOLD></C>");
                break;
            case 3: // 加粗+居中+放大
                content.append("<CB>").append(storeName).append("</CB>");
                break;
        }
        content.append("<BR>");

        // 取餐码仅增加突出区块，其余板块仍按同一模板规则打印。
        if (pickupCodeFocused) {
            content.append(buildDividerLine(paperWidth));
            content.append("<C><BOLD>您的取餐码</BOLD></C><BR>");
            String pickupCode = orderVO.getPickupCode();
            content.append("<CB>")
                    .append(pickupCode == null || pickupCode.isBlank() ? "暂无" : pickupCode)
                    .append("</CB><BR>");
            content.append("<C>(等餐期间请注意听取叫号)</C><BR>");
        }

        // ============ 营业时间 ============
        if (template.getShowBusinessHours() != null && template.getShowBusinessHours() == StatusEnum.getByCode(1) &&
                orderVO.getBusinessHours() != null && !orderVO.getBusinessHours().isEmpty()) {
            content.append("<C>营业时间：").append(orderVO.getBusinessHours()).append("</C><BR>");
            content.append("<BR>");
        }

        // 厨房可直接区分追加菜品与首次下单。
        if (addItems) content.append("<CB>加菜单</CB><BR>");

        // ============ 订单信息 ============
        content.append(buildDividerLine(paperWidth));

        // 解析订单信息显示字段
        List<String> orderFields = parseOrderInfoFields(template.getOrderInfoFields());
        for (String field : orderFields) {
            switch (field) {
                case "orderNo":
                    appendField(content, "订单号：", orderVO.getOrderNo(), paperWidth);
                    break;
                case "tableName":
                    if (orderVO.getTableName() != null) {
                        appendField(content, "桌　号：", orderVO.getTableName(), paperWidth);
                    }
                    break;
                case "orderType":
                    if (orderVO.getOrderType() != null) {
                        appendField(content, "类　型：", orderVO.getOrderType().getDesc(), paperWidth);
                    }
                    break;
                case "diningCount":
                    if (orderVO.getDiningCount() != null) {
                        appendField(content, "就餐人数：", orderVO.getDiningCount() + "人", paperWidth);
                    }
                    break;
                case "pickupMode":
                    if (orderVO.getPickupMode() != null) {
                        appendField(content, "取餐方式：", orderVO.getPickupMode().getDesc(), paperWidth);
                    }
                    break;
                case "pickupCode":
                    if (orderVO.getPickupCode() != null && !orderVO.getPickupCode().isEmpty()) {
                        appendField(content, "取餐码：", orderVO.getPickupCode(), paperWidth);
                    }
                    break;
                case "createdAt":
                    LocalDateTime receiptTime = addItems && orderVO.getItems() != null
                            ? orderVO.getItems().stream()
                                .map(RestaurantOrderVO.OrderItemVO::getAddTime)
                                .filter(java.util.Objects::nonNull)
                                .findFirst().orElse(orderVO.getCreatedAt())
                            : orderVO.getCreatedAt();
                    if (receiptTime != null) {
                        appendField(content, addItems ? "加菜时间：" : "下单时间：",
                                receiptTime.format(DATE_FORMATTER), paperWidth);
                    }
                    break;
            }
        }
        // ============ 顶部地址和电话 ============
        if (template.getShowAddress() != null && template.getShowAddress() == StatusEnum.getByCode(1)) {
            appendField(content, "商家地址：", orderVO.getAddress(), paperWidth);
        }
        if (template.getShowPhone() != null && template.getShowPhone() == StatusEnum.getByCode(1)) {
            appendField(content, "商家电话：", orderVO.getPhone(), paperWidth);
        }
        content.append(buildDividerLine(paperWidth));

        // ============ 菜品列表 ============
        Integer dishListStyle = template.getDishListStyle() != null ? template.getDishListStyle() : 0;

        // 表头与明细共用同一组字节列宽，金额始终落在首行。
        int priceWidth = paperWidth > 30 ? 8 : 6;
        int quantityWidth = paperWidth > 30 ? 5 : 4;
        int amountWidth = paperWidth > 30 ? 9 : 8;
        int nameWidth = paperWidth - priceWidth - quantityWidth - amountWidth;
        content.append(formatText("菜品", nameWidth))
                .append(formatRightAlign("单价", priceWidth))
                .append(formatCenter("数量", quantityWidth))
                .append(formatRightAlign("金额", amountWidth)).append("<BR>");
        content.append(buildDashLine(paperWidth));

        // 菜品明细
        List<RestaurantOrderVO.OrderItemVO> items = orderVO.getItems();

        for (RestaurantOrderVO.OrderItemVO item : items) {
            String dishName = item.getDishName();
            BigDecimal price = item.getUnitSellingPrice();
            Integer quantity = item.getQuantity();
            BigDecimal itemTotal = item.getLinePayableAmount();
            BigDecimal discountPrice = item.getDiscountAmount();
            Integer isGift = item.getIsGift();

            String priceStr = formatPrice(price);
            String quantityStr = String.valueOf(quantity);
            String totalStr = formatPrice(itemTotal);
            int rowPriceWidth = Math.max(priceWidth, textWidth(priceStr));
            int rowQuantityWidth = Math.max(quantityWidth, textWidth(quantityStr));
            int rowAmountWidth = Math.max(amountWidth, textWidth(totalStr));
            int rowNameWidth = paperWidth - rowPriceWidth - rowQuantityWidth - rowAmountWidth;
            String itemLabel = (dishName == null ? "" : dishName)
                    + (isGift != null && isGift == 1 ? "(赠)" : "");
            // 长菜名先完整打印，价格、数量、金额仍在同一行且金额位于右侧。
            String formattedDishName;
            if (rowNameWidth < 4 || textWidth(itemLabel) > rowNameWidth) {
                appendWrappedLine(content, itemLabel, paperWidth);
                formattedDishName = " ".repeat(Math.max(0, rowNameWidth));
            } else {
                formattedDishName = formatText(itemLabel, rowNameWidth);
            }
            content.append(formattedDishName)
                    .append(formatRightAlign(priceStr, rowPriceWidth))
                    .append(formatCenter(quantityStr, rowQuantityWidth))
                    .append(formatRightAlign(totalStr, rowAmountWidth));
            content.append("<BR>");
            if (discountPrice != null && discountPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal originalTotal = (itemTotal == null ? BigDecimal.ZERO : itemTotal).add(discountPrice);
                content.append("<RIGHT>原价 ").append(formatPrice(originalTotal))
                        .append(" 优惠 ").append(formatPrice(discountPrice)).append("</RIGHT><BR>");
            }

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
        if (!addItems && orderVO.getServiceFee() != null && orderVO.getServiceFee().compareTo(BigDecimal.ZERO) > 0) {
            content.append("<RIGHT>服务费：").append(formatPrice(orderVO.getServiceFee())).append("元</RIGHT><BR>");
        }

        // 优惠金额
        if (!addItems && orderVO.getDiscountAmount() != null && orderVO.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            content.append("<RIGHT>优惠：-").append(formatPrice(orderVO.getDiscountAmount())).append("元</RIGHT><BR>");
        }

        // 总计
        BigDecimal total = addItems ? items.stream()
                .map(RestaurantOrderVO.OrderItemVO::getLinePayableAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add) : orderVO.getTotalAmount();
        content.append("<RIGHT><BOLD>").append(addItems ? "本次加菜：" : "总计：")
                .append(formatPrice(total)).append("元</BOLD></RIGHT><BR>");
        content.append(buildDividerLine(paperWidth));

        // ============ 订单备注 ============
        if (template.getShowOrderNote() != null && template.getShowOrderNote() == StatusEnum.getByCode(1) &&
                orderVO.getNote() != null && !orderVO.getNote().isEmpty() && !"null".equals(orderVO.getNote())) {
            content.append("订单备注：").append(orderVO.getNote()).append("<BR>");
        }

        // ============ 支付信息 ============
        if (!addItems && template.getShowPaymentInfo() != null
                && template.getShowPaymentInfo() == StatusEnum.getByCode(1)) {
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
        if (scanCode != null && !scanCode.isBlank()) {
            content.append("<C>扫码完成出餐</C><BR><QR>").append(scanCode).append("</QR><BR>");
        } else if (template.getShowQrcode() != null && template.getShowQrcode() == StatusEnum.getByCode(1) &&
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
        String value = text == null ? "" : text;
        int bytes = textWidth(value);
        if (bytes > maxBytes) {
            StringBuilder cut = new StringBuilder();
            int used = 0;
            for (int offset = 0; offset < value.length();) {
                int codePoint = value.codePointAt(offset);
                String glyph = new String(Character.toChars(codePoint));
                int glyphWidth = textWidth(glyph);
                if (used + glyphWidth > maxBytes - 2) break;
                cut.append(glyph);
                used += glyphWidth;
                offset += Character.charCount(codePoint);
            }
            value = cut + "..";
            bytes = textWidth(value);
        }
        return value + " ".repeat(Math.max(0, maxBytes - bytes));
    }

    private static int textWidth(String value) {
        return value == null ? 0 : value.getBytes(PRINT_CHARSET).length;
    }

    /** 长订单号和地址主动分行，避免由打印机在任意位置自动折行。 */
    private static void appendField(StringBuilder content, String label, String value, int paperWidth) {
        String safeValue = value == null ? "" : value;
        if (textWidth(label + safeValue) <= paperWidth) {
            content.append(label).append(safeValue).append("<BR>");
            return;
        }
        content.append(label).append("<BR>");
        appendWrappedLine(content, "  " + safeValue, paperWidth);
    }

    private static void appendWrappedLine(StringBuilder content, String value, int paperWidth) {
        StringBuilder line = new StringBuilder();
        int width = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            int glyphWidth = textWidth(glyph);
            if (width + glyphWidth > paperWidth && width > 0) {
                content.append(line).append("<BR>");
                line.setLength(0);
                width = 0;
            }
            line.append(glyph);
            width += glyphWidth;
            offset += Character.charCount(codePoint);
        }
        content.append(line).append("<BR>");
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
     * 右对齐格式化
     */
    private static String formatRightAlign(String text, int width) {
        int len = textWidth(text);
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
        int len = textWidth(text);
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
