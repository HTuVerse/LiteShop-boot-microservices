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
import java.util.ArrayList;
import java.util.Comparator;
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
        return buildOrderContent(template, orderVO, "ORDER", template.getPaperWidth());
    }

    /** 正式打印按设备实际纸宽排版，避免把 80mm 模板的行宽发给 58mm 打印机。 */
    public static String buildOrderContent(PrintTemplate template, RestaurantOrderVO orderVO,
                                           PaperWidthEnum actualPaperWidth) {
        return buildOrderContent(template, orderVO, "ORDER", actualPaperWidth);
    }

    /** 后续下单只展示本批新增菜品与本批金额。 */
    public static String buildOrderContent(PrintTemplate template, RestaurantOrderVO orderVO,
                                           String printEvent, PaperWidthEnum actualPaperWidth) {
        return buildOrderContent(template, orderVO, printEvent, actualPaperWidth, null);
    }

    /** 正式小票可覆盖模板二维码，供设备扫码完成出餐。 */
    public static String buildOrderContent(PrintTemplate template, RestaurantOrderVO orderVO,
                                           String printEvent, PaperWidthEnum actualPaperWidth, String scanCode) {
        // 判断是否启用取餐码突出显示模式
        boolean isPickupCodeFocused = template.getShowPickupCode() != null &&
                template.getShowPickupCode() == BooleanEnum.YES && orderVO.getPickupMode()== pickupModeEnum.NUMBERCALLING;

        return buildNormalContent(template, orderVO, isPickupCodeFocused, actualPaperWidth,
                "ADD_ITEMS".equals(printEvent) || printEvent.startsWith("ITEM_TICKET"), scanCode);
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
        order.setCustomerName("张先生");
        order.setCustomerPhone("13812345678");
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
        order.setTotalAmount(new BigDecimal("88.00"));
        order.setActualAmount(new BigDecimal("80.00"));
        RestaurantOrderVO.OrderItemVO item = new RestaurantOrderVO.OrderItemVO();
        item.setDishName("示例菜品");
        item.setUnitListPrice(new BigDecimal("44.00"));
        item.setUnitSellingPrice(new BigDecimal("40.00"));
        item.setQuantity(2);
        item.setBatchOrderNote(order.getNote());
        item.setLinePayableAmount(new BigDecimal("80.00"));
        item.setDiscountAmount(new BigDecimal("8.00"));
        item.setSpecOptionsText("微辣");
        item.setNote("不要花生");
        order.setItems(List.of(item));
        return buildOrderContent(template, order, "ORDER", actualPaperWidth);
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
        if (template.getShowLogo() == StatusEnum.NORMAL) {
            // 小票机仅支持预先写入设备的 LOGO，图片 URL 的 IMG 指令属于标签机接口。
            content.append("<LOGO><BR>");
        }

        // ============ 店铺名称 ============
        String storeName = orderVO.getStoreName();
        Integer storeNameStyle = template.getStoreNameStyle() != null ? template.getStoreNameStyle() : 2;
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
                appendEnlargedCentered(content, storeName, paperWidth);
                break;
        }
        content.append("<BR>");

        // 营业时间及商家联系方式均属于门店信息，不混入取餐码或订单信息。
        if (template.getShowBusinessHours() == StatusEnum.NORMAL && hasText(orderVO.getBusinessHours())) {
            appendField(content, "营业时间：", orderVO.getBusinessHours(), paperWidth);
        }
        if (template.getShowAddress() == StatusEnum.NORMAL && hasText(orderVO.getAddress())) {
            appendField(content, "商家地址：", orderVO.getAddress(), paperWidth);
        }
        if (template.getShowPhone() == StatusEnum.NORMAL && hasText(orderVO.getPhone())) {
            appendField(content, "商家电话：", orderVO.getPhone(), paperWidth);
        }

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
                    if (!pickupCodeFocused && hasText(orderVO.getPickupCode())) {
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
                        appendField(content, "下单时间：",
                                receiptTime.format(DATE_FORMATTER), paperWidth);
                    }
                    break;
            }
        }
        // 客户信息单独成区；无会员或未勾选字段时不输出空区块。
        boolean showCustomerName = orderFields.contains("customerName") && hasText(orderVO.getCustomerName());
        boolean showCustomerPhone = orderFields.contains("customerPhone") && hasText(orderVO.getCustomerPhone());
        if (showCustomerName || showCustomerPhone) {
            content.append(buildDashLine(paperWidth));
            if (showCustomerName) appendField(content, "用户名：", orderVO.getCustomerName(), paperWidth);
            if (showCustomerPhone) {
                String phone = orderVO.getCustomerPhone();
                if (orderFields.contains("maskCustomerPhone")) phone = maskPhone(phone);
                appendField(content, "用户电话：", phone, paperWidth);
            }
        }
        content.append(buildDividerLine(paperWidth));

        // ============ 菜品列表 ============
        boolean showUnitPrice = template.getShowDishUnitPrice() != StatusEnum.DISABLED;
        boolean showQuantity = Integer.valueOf(1).equals(template.getItemTicketMode())
                || template.getShowDishQuantity() != StatusEnum.DISABLED;
        boolean showAmount = template.getShowDishAmount() != StatusEnum.DISABLED;

        // 每个金额字段独立控制，关闭的列把空间还给菜名。
        int priceWidth = showUnitPrice ? (paperWidth > 30 ? 8 : 7) : 0;
        int quantityWidth = showQuantity ? 6 : 0;
        int amountWidth = showAmount ? (paperWidth > 30 ? 9 : 8) : 0;
        int nameWidth = paperWidth - priceWidth - quantityWidth - amountWidth;
        content.append(formatText("菜品", nameWidth));
        if (showUnitPrice) content.append(formatRightAlign("单价", priceWidth));
        if (showQuantity) content.append(formatCenter("数量", quantityWidth));
        if (showAmount) content.append(formatRightAlign("金额", amountWidth));
        content.append("<BR>");
        content.append(buildDashLine(paperWidth));

        // 菜品明细
        List<RestaurantOrderVO.OrderItemVO> items = orderVO.getItems();
        // 只有发生过后续下单时才显示批次，且标记紧挨该批第一道菜。
        boolean showBatchLabels = items.stream().anyMatch(item -> batchNoOf(item) > 1);
        List<RestaurantOrderVO.OrderItemVO> itemsByBatch = new ArrayList<>(items);
        itemsByBatch.sort(Comparator.comparingInt(PrintContentBuilder::batchNoOf));
        int previousBatchNo = 0;
        String previousBatchNote = null;
        for (RestaurantOrderVO.OrderItemVO item : itemsByBatch) {
            int currentBatchNo = batchNoOf(item);
            if (previousBatchNo != 0 && currentBatchNo != previousBatchNo) {
                appendBatchNote(content, template, previousBatchNote);
            }
            if (showBatchLabels && currentBatchNo != previousBatchNo) {
                content.append("<BOLD>第").append(currentBatchNo).append("次下单</BOLD><BR>");
            }
            if (currentBatchNo != previousBatchNo) {
                // 历史首次下单沿用订单备注；新订单读取提交时的批次快照。
                previousBatchNote = item.getBatchOrderNote() == null && currentBatchNo == 1
                        ? orderVO.getNote() : item.getBatchOrderNote();
            }
            previousBatchNo = currentBatchNo;
            String dishName = item.getDishName();
            // 小票单价保留下单时的标价，优惠只体现在本行应付和合计中。
            BigDecimal price = item.getUnitListPrice() != null
                    ? item.getUnitListPrice() : item.getUnitSellingPrice();
            Integer quantity = item.getQuantity();
            BigDecimal itemTotal = item.getLinePayableAmount();
            BigDecimal discountPrice = item.getDiscountAmount();
            Integer isGift = item.getIsGift();

            String priceStr = showUnitPrice ? formatPrice(price) : "";
            String quantityStr = showQuantity ? String.valueOf(quantity) : "";
            String totalStr = showAmount ? formatPrice(itemTotal) : "";
            int rowPriceWidth = showUnitPrice ? Math.max(priceWidth, textWidth(priceStr)) : 0;
            int rowQuantityWidth = showQuantity ? Math.max(quantityWidth, textWidth(quantityStr)) : 0;
            int rowAmountWidth = showAmount ? Math.max(amountWidth, textWidth(totalStr)) : 0;
            int rowNameWidth = paperWidth - rowPriceWidth - rowQuantityWidth - rowAmountWidth;
            String itemLabel = (dishName == null ? "" : dishName)
                    + (isGift != null && isGift == 1 ? "(赠)" : "");
            // 菜名占满首行可用列，剩余部分在金额行之后续行。
            int firstNameWidth = Math.max(0, rowNameWidth);
            int firstEnd = prefixEnd(itemLabel, firstNameWidth);
            content.append(formatText(itemLabel.substring(0, firstEnd), firstNameWidth));
            if (showUnitPrice) content.append(formatRightAlign(priceStr, rowPriceWidth));
            if (showQuantity) content.append(formatCenter(quantityStr, rowQuantityWidth));
            if (showAmount) content.append(formatRightAlign(totalStr, rowAmountWidth));
            content.append("<BR>");
            if (firstEnd < itemLabel.length()) {
                appendWrappedLine(content, "  " + itemLabel.substring(firstEnd), paperWidth);
            }
            if (discountPrice != null && discountPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal originalTotal = (itemTotal == null ? BigDecimal.ZERO : itemTotal).add(discountPrice);
                if (template.getShowDishOriginalPrice() != StatusEnum.DISABLED) {
                    content.append("<RIGHT>原价：").append(formatPrice(originalTotal)).append("</RIGHT><BR>");
                }
                if (template.getShowDishDiscountPrice() != StatusEnum.DISABLED) {
                    content.append("<RIGHT>优惠价：").append(formatPrice(itemTotal)).append("</RIGHT><BR>");
                }
            }

            // 显示规格信息
            if (template.getShowDishSpec() == StatusEnum.NORMAL &&
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
            if (template.getShowDishNote() == StatusEnum.NORMAL &&
                    item.getNote() != null && !item.getNote().isEmpty() && !"null".equals(item.getNote())) {
                content.append("  备注：").append(item.getNote()).append("<BR>");
            }
        }
        appendBatchNote(content, template, previousBatchNote);

        // ============ 合计部分 ============
        if (template.getShowOrderTotal() != StatusEnum.DISABLED) {
            content.append(buildDashLine(paperWidth));

            BigDecimal originalAmount = addItems ? items.stream()
                    .map(item -> {
                        BigDecimal linePayable = item.getLinePayableAmount() == null
                                ? BigDecimal.ZERO : item.getLinePayableAmount();
                        BigDecimal lineDiscount = item.getDiscountAmount() == null
                                ? BigDecimal.ZERO : item.getDiscountAmount();
                        return linePayable.add(lineDiscount);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add) : orderVO.getTotalAmount();
            content.append("<RIGHT>").append(addItems ? "本次原价：" : "商品金额：")
                    .append(formatPrice(originalAmount)).append("元</RIGHT><BR>");
            // 后续批次只计算本次新增菜品，不重复打印整单费用。
            if (!addItems && orderVO.getServiceFee() != null && orderVO.getServiceFee().compareTo(BigDecimal.ZERO) > 0) {
                content.append("<RIGHT>服务费：").append(formatPrice(orderVO.getServiceFee())).append("元</RIGHT><BR>");
            }
            BigDecimal discount = addItems ? items.stream()
                    .map(RestaurantOrderVO.OrderItemVO::getDiscountAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add) : orderVO.getDiscountAmount();
            if (discount != null && discount.compareTo(BigDecimal.ZERO) > 0) {
                content.append("<RIGHT>优惠：-").append(formatPrice(discount)).append("元</RIGHT><BR>");
            }

            BigDecimal payable = addItems ? items.stream()
                    .map(RestaurantOrderVO.OrderItemVO::getLinePayableAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add) : orderVO.getActualAmount();
            if (payable == null) payable = orderVO.getTotalAmount();
            content.append("<RIGHT><BOLD>").append(addItems ? "本次应付：" : "应付金额：")
                    .append(formatPrice(payable)).append("元</BOLD></RIGHT><BR>");
            content.append(buildDividerLine(paperWidth));
        }

        // ============ 支付信息 ============
        if (template.getShowPaymentInfo() != null
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
        if (template.getShowQrcode() == StatusEnum.NORMAL && scanCode != null && !scanCode.isBlank()) {
            content.append("<C>扫码完成出餐</C><BR><QR>").append(scanCode).append("</QR><BR>");
        } else if (!addItems && template.getShowQrcode() != null && template.getShowQrcode() == StatusEnum.getByCode(1) &&
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

    /** 历史订单项没有批次号时按首次下单处理。 */
    private static int batchNoOf(RestaurantOrderVO.OrderItemVO item) {
        Integer batchNo = item.getBatchNo();
        return batchNo == null || batchNo < 1 ? 1 : batchNo;
    }

    /** 把订单备注紧跟在所属批次菜品之后。 */
    private static void appendBatchNote(StringBuilder content, PrintTemplate template, String note) {
        if (template.getShowOrderNote() == StatusEnum.NORMAL && hasText(note) && !"null".equals(note)) {
            content.append("订单备注：").append(note).append("<BR>");
        }
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

    /** 放大店名按设备可打印宽度分行，保留配置的字号。 */
    private static void appendEnlargedCentered(StringBuilder content, String value, int paperWidth) {
        String remaining = value == null ? "" : value;
        int lineWidth = Math.max(1, paperWidth / 2);
        while (!remaining.isEmpty()) {
            int end = prefixEnd(remaining, lineWidth);
            if (end == 0) end = Character.charCount(remaining.codePointAt(0));
            content.append("<CB>").append(remaining, 0, end).append("</CB>");
            remaining = remaining.substring(end);
            if (!remaining.isEmpty()) content.append("<BR>");
        }
    }

    /** 按 GBK 字节宽度取得能放入本行的字符边界。 */
    private static int prefixEnd(String value, int width) {
        int used = 0;
        int offset = 0;
        while (offset < value.length()) {
            int codePoint = value.codePointAt(offset);
            int glyphWidth = textWidth(new String(Character.toChars(codePoint)));
            if (used + glyphWidth > width) break;
            used += glyphWidth;
            offset += Character.charCount(codePoint);
        }
        return offset;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 仅对标准 11 位手机号隐藏中间四位，其他号码保持原样。 */
    private static String maskPhone(String phone) {
        return phone != null && phone.matches("1\\d{10}")
                ? phone.substring(0, 3) + "****" + phone.substring(7) : phone;
    }

    /** 长订单号和地址使用首行剩余宽度，续行缩进两格。 */
    private static void appendField(StringBuilder content, String label, String value, int paperWidth) {
        String safeValue = value == null ? "" : value;
        int firstEnd = prefixEnd(safeValue, Math.max(0, paperWidth - textWidth(label)));
        content.append(label).append(safeValue, 0, firstEnd).append("<BR>");
        if (firstEnd < safeValue.length()) {
            appendWrappedLine(content, "  " + safeValue.substring(firstEnd), paperWidth);
        }
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
