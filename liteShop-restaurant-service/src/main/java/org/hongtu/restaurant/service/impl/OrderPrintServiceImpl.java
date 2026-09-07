package org.hongtu.restaurant.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.enums.StatusEnum;
import org.hongtu.restaurant.DAO.RestaurantOrderMapper;
import org.hongtu.restaurant.DTO.PrintTemplate;
import org.hongtu.restaurant.DTO.PrinterInfo;
import org.hongtu.restaurant.DTO.RestaurantOrder;
import org.hongtu.restaurant.convert.RestaurantOrderConverter;
import org.hongtu.restaurant.service.OrderPrintService;
import org.hongtu.restaurant.service.PrintTemplateService;
import org.hongtu.restaurant.service.PrinterInfoService;
import org.hongtu.restaurant.service.RestaurantOrderService;
import org.hongtu.restaurant.utils.FeieyunPrinterUtil;
import org.hongtu.restaurant.utils.PrintContentBuilder;
import org.hongtu.restaurant.VO.RestaurantOrderVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单打印服务实现类
 *
 * @author hongtu
 */
@Slf4j
@Service
public class OrderPrintServiceImpl implements OrderPrintService {

    @Resource
    private RestaurantOrderMapper restaurantOrderMapper;

    @Resource
    private PrinterInfoService printerInfoService;

    @Resource
    private PrintTemplateService printTemplateService;

    @Resource
    private FeieyunPrinterUtil feieyunPrinterUtil;

    @Resource
    RestaurantOrderConverter restaurantOrderConverter;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Map<String, Object> printOrder(Long orderId) {
        log.info("打印订单（使用默认打印机）：orderId={}", orderId);

        // 1. 获取订单信息
        RestaurantOrder restaurantOrder = restaurantOrderMapper.selectById(orderId);
        RestaurantOrderVO orderVO = restaurantOrderConverter.toVO(restaurantOrder);
        if (orderVO == null) {
            return buildErrorResult("订单不存在");
        }

        // 2. 获取门店默认打印机
        PrinterInfo printer = printerInfoService.getDefaultPrinterByStore(orderVO.getStoreId());
        if (printer == null) {
            return buildErrorResult("该门店未配置打印机");
        }

        // 3. 未传模板时使用门店默认模板
        Long templateId = null;
        if (orderVO.getStoreId() != null) {
            PrintTemplate defaultTemplate = printTemplateService.getTemplateByStore(orderVO.getStoreId());
            if (defaultTemplate != null && defaultTemplate.getId() != null) {
                templateId = defaultTemplate.getId();
            }
        }

        // 3. 打印订单
        return printOrderVO(orderVO, printer.getId(), templateId);
    }

    @Override
    public Map<String, Object> printOrder(Long orderId, Long printerId) {
        return printOrder(orderId, printerId, null);
    }

    @Override
    public Map<String, Object> printOrder(Long orderId, Long printerId, Long templateId) {
        log.info("打印订单：orderId={}, printerId={}, templateId={}", orderId, printerId, templateId);

        // 1. 获取订单信息
        RestaurantOrder restaurantOrder = restaurantOrderMapper.selectById(orderId);
        RestaurantOrderVO orderVO = restaurantOrderConverter.toVO(restaurantOrder);
        if (orderVO == null) {
            return buildErrorResult("订单不存在");
        }

        // 2. 留空时使用门店默认打印机
        if (printerId == null && orderVO.getStoreId() != null) {
            PrinterInfo defaultPrinter = printerInfoService.getDefaultPrinterByStore(orderVO.getStoreId());
            if (defaultPrinter == null) {
                return buildErrorResult("该门店未配置打印机");
            }
            printerId = defaultPrinter.getId();
        }
        if (printerId == null) {
            return buildErrorResult("请选择打印机或确保门店已配置默认打印机");
        }

        // 3. 留空时使用门店默认模板
        if (templateId == null && orderVO.getStoreId() != null) {
            PrintTemplate defaultTemplate = printTemplateService.getTemplateByStore(orderVO.getStoreId());
            if (defaultTemplate != null && defaultTemplate.getId() != null) {
                templateId = defaultTemplate.getId();
            }
        }

        // 4. 打印订单
        return printOrderVO(orderVO, printerId, templateId);
    }

    @Override
    public Map<String, Object> printOrderVO(RestaurantOrderVO orderVO, Long printerId, Long templateId) {
        try {
            // 1. 验证打印机
            PrinterInfo printer = printerInfoService.getById(printerId);
            if (printer == null) {
                return buildErrorResult("打印机不存在");
            }

            if (printer.getEnabled() == null || printer.getEnabled() == StatusEnum.getByCode(0)) {
                return buildErrorResult("打印机已禁用");
            }

            // 2. 获取打印模板
            PrintTemplate template = printTemplateService.getById(templateId);
            if (templateId != null) {
                // 优先使用指定的模板ID
                if (template == null) {
                    log.warn("指定的模板不存在，使用打印机配置的模板");
                    // 使用打印机配置的模板
                    if (printer.getTemplateId() != null) {
                        template = printTemplateService.getById(printer.getTemplateId());
                    }
                    // 如果打印机未配置模板，使用门店默认模板
                    if (template == null) {
                        template = printTemplateService.getTemplateByStore(orderVO.getStoreId());
                    }
                }
            } else {
                // 使用打印机配置的模板
                if (printer.getTemplateId() != null) {
                    template = printTemplateService.getById(printer.getTemplateId());
                }
                // 如果打印机未配置模板，使用门店默认模板
                if (template == null) {
                    template = printTemplateService.getTemplateByStore(orderVO.getStoreId());
                }
            }

            // 3. 构建打印内容
            String printContent = PrintContentBuilder.buildOrderContent(template, orderVO);
            log.debug("打印内容：\n{}", printContent);

            // 4. 调用飞鹅打印接口
            Integer printCopies = template.getPrintCopies() != null ? template.getPrintCopies() : 1;
            String result = feieyunPrinterUtil.print(printer.getSn(), printContent, printCopies);

            // 5. 解析打印结果
            JSONObject resultJson = JSON.parseObject(result);
            int ret = resultJson.getIntValue("ret");
            String msg = resultJson.getString("msg");
            String printOrderId = resultJson.getString("data");

            Map<String, Object> response = new HashMap<>();
            if (ret == 0) {
                log.info("✅ 打印成功 - 订单ID: {}, 打印订单ID: {}", orderVO.getId(), printOrderId);
                response.put("success", true);
                response.put("msg", "打印成功");
                response.put("printOrderId", printOrderId);
                response.put("printerSn", printer.getSn());
                response.put("printerName", printer.getRemark());
                response.put("printCopies", printCopies);
                response.put("printTime", LocalDateTime.now().format(DATE_FORMATTER));
            } else {
                log.error("❌ 打印失败 - 订单ID: {}, 错误信息: {}", orderVO.getId(), msg);
                response.put("success", false);
                response.put("msg", "打印失败：" + msg);
                response.put("errorCode", ret);
            }

            return response;

        } catch (Exception e) {
            log.error("打印订单异常", e);
            return buildErrorResult("打印订单失败：" + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> printAddItems(Long orderId, List<Long> orderItemIds, Long printerId, Long templateId) {
        if (orderId == null || orderItemIds == null || orderItemIds.isEmpty()) {
            return buildErrorResult("订单ID或加菜项不能为空");
        }
        log.info("打印加菜单：orderId={}, orderItemIds={}, printerId={}, templateId={}", orderId, orderItemIds, printerId, templateId);

        RestaurantOrder restaurantOrder = restaurantOrderMapper.selectById(orderId);
        RestaurantOrderVO orderVO = restaurantOrderConverter.toVO(restaurantOrder);
        if (orderVO == null) {
            return buildErrorResult("订单不存在");
        }
        List<RestaurantOrderVO.OrderItemVO> allItems = orderVO.getItems();
        if (allItems != null) {
            List<RestaurantOrderVO.OrderItemVO> addItemsOnly = allItems.stream()
                    .filter(item -> item.getId() != null && orderItemIds.contains(item.getId()))
                    .collect(Collectors.toList());
            orderVO.setItems(addItemsOnly);
        }
        return printOrderVO(orderVO, printerId, templateId);
    }

    @Override
    public Map<String, Object> batchPrintOrders(Long[] orderIds, Long printerId) {
        log.info("批量打印订单：orderIds={}, printerId={}", orderIds, printerId);

        int successCount = 0;
        int failCount = 0;
        StringBuilder failMessages = new StringBuilder();

        for (Long orderId : orderIds) {
            try {
                Map<String, Object> result = printerId != null ?
                        printOrder(orderId, printerId) : printOrder(orderId);

                boolean success = (boolean) result.getOrDefault("success", false);
                if (success) {
                    successCount++;
                } else {
                    failCount++;
                    failMessages.append("订单").append(orderId).append("：")
                            .append(result.get("msg")).append("；");
                }
            } catch (Exception e) {
                failCount++;
                failMessages.append("订单").append(orderId).append("：")
                        .append(e.getMessage()).append("；");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("totalCount", orderIds.length);
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        if (failCount > 0) {
            result.put("failMessages", failMessages.toString());
        }

        log.info("批量打印完成 - 总数: {}, 成功: {}, 失败: {}", orderIds.length, successCount, failCount);
        return result;
    }

    @Override
    public Map<String, Object> testPrint(Long printerId, Long templateId) {
        log.info("测试打印：printerId={}, templateId={}", printerId, templateId);

        try {
            // 1. 验证打印机
            PrinterInfo printer = printerInfoService.getById(printerId);
            if (printer == null) {
                return buildErrorResult("打印机不存在");
            }

            Map<String, Object> result = this.printOrder(93L, printerId,templateId);

            return result;

        } catch (Exception e) {
            log.error("测试打印异常", e);
            return buildErrorResult("测试打印失败：" + e.getMessage());
        }
    }

    /**
     * 构建错误结果
     */
    private Map<String, Object> buildErrorResult(String msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("msg", msg);
        return result;
    }
}
