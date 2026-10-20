package org.hongtu.restaurant.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.enums.StatusEnum;
import org.hongtu.restaurant.DAO.RestaurantOrderMapper;
import org.hongtu.restaurant.DAO.PrintJobMapper;
import org.hongtu.restaurant.DTO.PrintJob;
import org.hongtu.restaurant.DTO.PrintTemplate;
import org.hongtu.restaurant.DTO.PrinterInfo;
import org.hongtu.restaurant.DTO.RestaurantOrder;
import org.hongtu.restaurant.enums.PaperWidthEnum;
import org.hongtu.restaurant.convert.RestaurantOrderConverter;
import org.hongtu.restaurant.service.OrderPrintService;
import org.hongtu.restaurant.service.PrintTemplateService;
import org.hongtu.restaurant.service.PrinterInfoService;
import org.hongtu.restaurant.service.RestaurantOrderService;
import org.hongtu.restaurant.utils.FeieyunPrinterUtil;
import org.hongtu.restaurant.utils.PrintContentBuilder;
import org.hongtu.restaurant.utils.ReceiptScanCode;
import org.hongtu.restaurant.VO.RestaurantOrderVO;
import org.hongtu.utils.BeanCopyUtils;
import org.hongtu.DTO.SysUser;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private PrintJobMapper printJobMapper;

    @Resource
    private UserFeignClient userFeignClient;

    @Resource
    RestaurantOrderConverter restaurantOrderConverter;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Map<String, Object> printOrder(Long orderId) {
        log.info("打印订单（使用默认打印机）：orderId={}", orderId);

        // 1. 获取订单信息
        RestaurantOrder restaurantOrder = restaurantOrderMapper.selectById(orderId);
        if (restaurantOrder == null) {
            return buildErrorResult("订单不存在");
        }
        RestaurantOrderVO orderVO = restaurantOrderConverter.toVO(restaurantOrder);

        // 2. 获取门店默认打印机
        PrinterInfo printer = printerInfoService.getDefaultPrinterByStore(orderVO.getStoreId());
        if (printer == null) {
            return buildErrorResult("该门店未配置打印机");
        }

        // 留空时由打印流程优先使用打印机绑定的模板。
        return printOrderVO(orderVO, printer.getId(), null);
    }

    @Override
    public Map<String, Object> printOrder(Long orderId, Long printerId) {
        return printOrder(orderId, printerId, null);
    }

    @Override
    public Map<String, Object> printOrder(Long orderId, Long printerId, Long templateId) {
        return printOrder(orderId, printerId, templateId, false);
    }

    @Override
    public Map<String, Object> printOrderManually(Long orderId, Long printerId, Long templateId) {
        return printOrder(orderId, printerId, templateId, true);
    }

    private Map<String, Object> printOrder(Long orderId, Long printerId, Long templateId, boolean manual) {
        log.info("打印订单：orderId={}, printerId={}, templateId={}", orderId, printerId, templateId);

        // 1. 获取订单信息
        RestaurantOrder restaurantOrder = restaurantOrderMapper.selectById(orderId);
        if (restaurantOrder == null) {
            return buildErrorResult("订单不存在");
        }
        RestaurantOrderVO orderVO = restaurantOrderConverter.toVO(restaurantOrder);

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

        // 留空时优先使用打印机绑定的模板，再回退到门店默认模板。
        return printOrderVO(orderVO, printerId, templateId, manual);
    }

    @Override
    public Map<String, Object> printOrderVO(RestaurantOrderVO orderVO, Long printerId, Long templateId) {
        return printOrderVO(orderVO, printerId, templateId, false);
    }

    private Map<String, Object> printOrderVO(RestaurantOrderVO orderVO, Long printerId, Long templateId,
                                              boolean manual) {
        return printOrderVO(orderVO, printerId, templateId, manual, "ORDER", "receipt");
    }

    /** 按打印机绑定模板出单，并为每次加菜登记独立的打印任务。 */
    private Map<String, Object> printOrderVO(RestaurantOrderVO orderVO, Long printerId, Long templateId,
                                              boolean manual, String printEvent, String jobSuffix) {
        PrintJob printJob = null;
        try {
            // 1. 验证打印机
            PrinterInfo printer = printerInfoService.getById(printerId);
            if (printer == null) {
                return buildErrorResult("打印机不存在");
            }
            if (!Objects.equals(printer.getStoreId(), orderVO.getStoreId())) {
                return buildErrorResult("打印机与订单不属于同一门店");
            }

            if (printer.getEnabled() == null || printer.getEnabled() == StatusEnum.getByCode(0)) {
                return buildErrorResult("打印机已禁用");
            }

            // 打印时机只决定菜品范围；同一台设备始终使用它绑定的小票模板。
            PrintTemplate template = printer.getTemplateId() == null ? null
                    : printTemplateService.getById(printer.getTemplateId());
            if (printer.getTemplateId() != null && template == null) {
                return buildErrorResult("打印机绑定的模板不存在，请重新配置");
            }
            if (template == null && templateId != null) template = printTemplateService.getById(templateId);
            if (template == null) template = printTemplateService.getTemplateByStore(orderVO.getStoreId());
            if (template == null) {
                return buildErrorResult("门店未配置可用打印模板");
            }
            if (template.getEnabled() == StatusEnum.DISABLED) {
                return buildErrorResult("打印机绑定的模板已禁用");
            }
            if (template.getStoreId() != null && !Objects.equals(template.getStoreId(), orderVO.getStoreId())) {
                return buildErrorResult("打印模板与订单不属于同一门店");
            }

            // 一菜一票按订单明细拆分；相同菜品的数量仍在同一张票上显示。
            if (Integer.valueOf(1).equals(template.getItemTicketMode()) && orderVO.getItems() != null
                    && !orderVO.getItems().isEmpty() && !jobSuffix.contains(":item:")) {
                Map<String, Object> firstResult = null;
                int ticketCount = 0;
                List<RestaurantOrderVO.OrderItemVO> ticketItems = orderVO.getItems().stream()
                        .sorted(Comparator.comparingInt(item -> item.getBatchNo() == null ? 1 : item.getBatchNo()))
                        .toList();
                for (RestaurantOrderVO.OrderItemVO item : ticketItems) {
                    RestaurantOrderVO itemOrderVO = BeanCopyUtils.copyBean(orderVO, RestaurantOrderVO.class);
                    itemOrderVO.setItems(List.of(item));
                    boolean firstBatchTicket = "ORDER".equals(printEvent) && ticketCount == 0
                            && (item.getBatchNo() == null || item.getBatchNo() == 1);
                    String itemEvent = "ADD_ITEMS".equals(printEvent) ? "ADD_ITEMS"
                            : firstBatchTicket ? "ITEM_TICKET" : "ITEM_TICKET_NO_QR";
                    Map<String, Object> result = printOrderVO(itemOrderVO, printerId, templateId, manual,
                            itemEvent, jobSuffix + ":item:" + item.getId());
                    if (!Boolean.TRUE.equals(result.get("success"))) return result;
                    if (firstResult == null) firstResult = result;
                    ticketCount++;
                }
                firstResult.put("ticketCount", ticketCount);
                return firstResult;
            }

            // 3. 构建打印内容
            fillCustomerForReceipt(orderVO);
            PaperWidthEnum paperWidth = printer.getPaperWidth() == null
                    ? PaperWidthEnum.MM_58 : printer.getPaperWidth();
            String scanCode = "ORDER".equals(printEvent) || "ITEM_TICKET".equals(printEvent)
                    ? ReceiptScanCode.forOrder(orderVO.getId()) : null;
            String printContent = PrintContentBuilder.buildOrderContent(template, orderVO, printEvent,
                    paperWidth, scanCode);
            log.debug("打印内容已生成: orderId={}, contentLength={}", orderVO.getId(), printContent.length());

            // 4. 调用飞鹅打印接口
            int printCopies = template.getPrintCopies() == null ? 1
                    : Math.max(1, Math.min(5, template.getPrintCopies()));
            String jobKey = orderVO.getId() + ":" + printer.getId() + ":" + jobSuffix;
            PrintJob existing = printJobMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PrintJob>()
                    .eq(PrintJob::getJobKey, jobKey)
                    .last("LIMIT 1"));
            // 自动打印保持幂等；手动打印可重新提交已结束的任务。
            if (existing != null && existing.getStatus() != null
                    && (existing.getStatus() == 5 || existing.getStatus() == 4
                    || !manual && existing.getStatus() != 2 && existing.getStatus() != 3)) {
                return buildSuccessQueued(existing);
            }
            printJob = existing == null ? new PrintJob() : existing;
            printJob.setOrderId(orderVO.getId());
            printJob.setStoreId(orderVO.getStoreId());
            printJob.setPrinterId(printer.getId());
            printJob.setPrinterSn(printer.getSn());
            printJob.setJobKey(jobKey);
            printJob.setContent(printContent);
            printJob.setPrintCopies(printCopies);
            printJob.setStatus(5);
            printJob.setAttempts(0);
            printJob.setLastError(null);
            printJob.setProviderOrderId(null);
            printJob.setCallbackTime(null);
            printJob.setCreatedAt(existing == null ? LocalDateTime.now() : existing.getCreatedAt());
            printJob.setUpdatedAt(LocalDateTime.now());
            if (existing == null) {
                printJobMapper.insert(printJob);
            } else {
                printJobMapper.updateById(printJob);
                // MyBatis-Plus 默认跳过 null 字段，显式清除上次投递结果。
                printJobMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PrintJob>()
                        .eq(PrintJob::getId, printJob.getId())
                        .set(PrintJob::getLastError, null)
                        .set(PrintJob::getProviderOrderId, null)
                        .set(PrintJob::getCallbackTime, null));
            }
            return buildSuccessQueued(printJob);
        } catch (Exception e) {
            log.error("登记打印任务失败", e);
            return buildErrorResult("登记打印任务失败");
        }
    }

    /** 按订单关联会员填充小票客户信息，查询失败不影响出单。 */
    private void fillCustomerForReceipt(RestaurantOrderVO orderVO) {
        if (orderVO.getUserId() == null || orderVO.getCustomerName() != null || orderVO.getCustomerPhone() != null) return;
        try {
            SysUser customer = FeignResultUtil.unwrap(userFeignClient.getUserById(orderVO.getUserId()));
            if (customer == null) return;
            orderVO.setCustomerName(customer.getNickname() == null || customer.getNickname().isBlank()
                    ? customer.getUsername() : customer.getNickname());
            orderVO.setCustomerPhone(customer.getPhone());
        } catch (Exception e) {
            log.warn("获取小票客户信息失败: orderId={}, userId={}", orderVO.getId(), orderVO.getUserId(), e);
        }
    }

    /**
     * 发送待打印任务。失败按次数重试，耗尽后保持失败状态供人工补打。
     */
    public void dispatchPendingJobs() {
        List<PrintJob> jobs = org.hongtu.tenant.TenantIgnore.get(() -> printJobMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PrintJob>()
                        .in(PrintJob::getStatus, 5, 3)
                        .lt(PrintJob::getAttempts, 5)
                        .last("LIMIT 50")));
        for (PrintJob job : jobs) {
            if (job.getTenantId() == null) {
                continue;
            }
            org.hongtu.tenant.TenantScope.run(job.getTenantId(), () -> sendJob(job));
        }
    }

    /**
     * 人工补打：失败任务重新进入待发送队列。
     */
    public boolean reprint(Long printJobId) {
        PrintJob job = printJobMapper.selectById(printJobId);
        if (job == null) {
            return false;
        }
        job.setStatus(5);
        job.setAttempts(0);
        job.setLastError(null);
        job.setUpdatedAt(LocalDateTime.now());
        return printJobMapper.updateById(job) > 0;
    }

    private void sendJob(PrintJob printJob) {
        try {
            PrinterInfo printer = printerInfoService.getById(printJob.getPrinterId());
            if (printer == null || printJob.getContent() == null) {
                printJob.setStatus(3);
                printJob.setLastError("打印机或打印内容不存在");
                printJob.setAttempts((printJob.getAttempts() == null ? 0 : printJob.getAttempts()) + 1);
                printJob.setUpdatedAt(LocalDateTime.now());
                printJobMapper.updateById(printJob);
                return;
            }
            String result = feieyunPrinterUtil.print(printer.getSn(), printJob.getContent(),
                    printJob.getPrintCopies() == null ? 1 : printJob.getPrintCopies());

            // 5. 解析打印结果
            JSONObject resultJson = JSON.parseObject(result);
            int ret = resultJson.getIntValue("ret");
            String msg = resultJson.getString("msg");
            String printOrderId = resultJson.getString("data");

            printJob.setProviderOrderId(printOrderId);
            printJob.setStatus(ret == 0 ? 0 : 3);
            printJob.setLastError(ret == 0 ? null : msg);
            printJob.setAttempts((printJob.getAttempts() == null ? 0 : printJob.getAttempts()) + 1);
            printJob.setUpdatedAt(LocalDateTime.now());
            printJobMapper.updateById(printJob);
        } catch (Exception e) {
            log.error("发送打印任务异常: printJobId={}", printJob.getId(), e);
            printJob.setStatus(3);
            printJob.setLastError(e.getMessage());
            printJob.setAttempts((printJob.getAttempts() == null ? 0 : printJob.getAttempts()) + 1);
            printJob.setUpdatedAt(LocalDateTime.now());
            printJobMapper.updateById(printJob);
        }
    }

    private Map<String, Object> buildSuccessQueued(PrintJob printJob) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("msg", "打印任务已登记");
        response.put("printJobId", printJob.getId());
        response.put("printerSn", printJob.getPrinterSn());
        return response;
    }

    @Override
    public Map<String, Object> printAddItems(Long orderId, List<Long> orderItemIds, Long printerId, Long templateId) {
        if (orderId == null || orderItemIds == null || orderItemIds.isEmpty()) {
            return buildErrorResult("订单ID或加菜项不能为空");
        }
        log.info("打印新增菜品批次：orderId={}, orderItemIds={}, printerId={}, templateId={}", orderId, orderItemIds, printerId, templateId);

        RestaurantOrder restaurantOrder = restaurantOrderMapper.selectById(orderId);
        if (restaurantOrder == null) {
            return buildErrorResult("订单不存在");
        }
        RestaurantOrderVO orderVO = restaurantOrderConverter.toVO(restaurantOrder);
        List<RestaurantOrderVO.OrderItemVO> allItems = orderVO.getItems();
        if (allItems == null) return buildErrorResult("订单没有可打印的加菜项");
        List<RestaurantOrderVO.OrderItemVO> addItemsOnly = allItems.stream()
                .filter(item -> item.getId() != null && orderItemIds.contains(item.getId()))
                .collect(Collectors.toList());
        if (addItemsOnly.isEmpty()) return buildErrorResult("指定的加菜项不属于该订单");
        orderVO.setItems(addItemsOnly);
        long firstItemId = addItemsOnly.stream().map(RestaurantOrderVO.OrderItemVO::getId)
                .min(Long::compareTo).orElseThrow();
        return printOrderVO(orderVO, printerId, templateId, false, "ADD_ITEMS", "add-items:" + firstItemId);
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
        return testPrint(printerId, templateId, null);
    }

    @Override
    public Map<String, Object> testPrint(Long printerId, Long templateId, PrintTemplate draftTemplate) {
        log.info("测试打印：printerId={}, templateId={}", printerId, templateId);

        try {
            // 1. 验证打印机
            PrinterInfo printer = printerInfoService.getById(printerId);
            if (printer == null) {
                return buildErrorResult("打印机不存在");
            }
            if (printer.getEnabled() == null || printer.getEnabled() == StatusEnum.getByCode(0)) {
                return buildErrorResult("打印机已禁用");
            }
            PrintTemplate template = draftTemplate;
            if (template == null && templateId != null) {
                template = printTemplateService.getById(templateId);
                if (template == null) {
                    return buildErrorResult("打印模板不存在");
                }
            }
            if (template == null && printer.getTemplateId() != null) {
                template = printTemplateService.getById(printer.getTemplateId());
            }
            if (template == null) {
                template = printTemplateService.getTemplateByStore(printer.getStoreId());
            }
            if (template == null) {
                return buildErrorResult("打印机未绑定模板，且门店没有可用的默认模板");
            }
            if (template != null && template.getStoreId() != null
                    && !Objects.equals(template.getStoreId(), printer.getStoreId())) {
                return buildErrorResult("打印模板与打印机不属于同一门店");
            }

            // 试打使用演示订单与正式打印共用的模板生成器，不读取真实订单。
            PaperWidthEnum paperWidth = printer.getPaperWidth() == null
                    ? PaperWidthEnum.MM_58 : printer.getPaperWidth();
            String content = PrintContentBuilder.buildSampleContent(template, printer.getStoreId(), paperWidth);
            int copies = template.getPrintCopies() == null ? 1
                    : Math.max(1, Math.min(5, template.getPrintCopies()));
            String providerResult = feieyunPrinterUtil.print(printer.getSn(), content, copies);
            JSONObject resultJson = JSON.parseObject(providerResult);
            int ret = resultJson.getIntValue("ret");
            if (ret != 0) {
                return buildErrorResult("测试打印失败：" + resultJson.getString("msg"));
            }
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("msg", "测试打印已提交");
            result.put("printOrderId", resultJson.getString("data"));
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
