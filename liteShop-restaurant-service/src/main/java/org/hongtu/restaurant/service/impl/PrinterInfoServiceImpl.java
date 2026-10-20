package org.hongtu.restaurant.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.StatusEnum;
import org.hongtu.restaurant.DTO.PrinterInfo;
import org.hongtu.restaurant.DTO.PrintTemplate;
import org.hongtu.restaurant.DAO.PrinterInfoMapper;
import org.hongtu.restaurant.enums.PrinterStatusEnum;
import org.hongtu.restaurant.enums.PrinterTypeEnum;
import org.hongtu.restaurant.enums.PaperWidthEnum;
import org.hongtu.restaurant.service.PrinterInfoService;
import org.hongtu.restaurant.service.PrintTemplateService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import org.hongtu.restaurant.utils.FeieyunPrinterUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 飞鹅打印机信息表 服务实现类
 * </p>
 *
 * @author Ht
 * @since 2025-01-26
 */
@Slf4j
@Service
public class PrinterInfoServiceImpl extends ServiceImpl<PrinterInfoMapper, PrinterInfo> implements PrinterInfoService {

    @Resource
    private PrinterInfoMapper printerInfoMapper;

    @Resource
    private FeieyunPrinterUtil feieyunPrinterUtil;

    @Resource
    private StoreDataScopeService storeDataScopeService;

    @Resource
    private PrintTemplateService printTemplateService;

    /**
     * 添加打印机（本地数据库 + 飞鹅云）
     * 
     * 流程：
     * 1. 检查本地是否已存在该打印机
     * 2. 调用飞鹅云API添加打印机
     * 3. 保存到本地数据库
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addPrinter(PrinterInfo printerInfo) {
        storeDataScopeService.checkStoreAccess(printerInfo.getStoreId());
        validateBoundTemplate(printerInfo.getTemplateId(), printerInfo.getStoreId());
        Map<String, Object> result = new HashMap<>();

        // 1. 检查是否已存在
        PrinterInfo existing = printerInfoMapper.selectBySn(printerInfo.getSn());
        if (existing != null) {
            result.put("success", false);
            result.put("msg", "打印机编号已存在：" + printerInfo.getSn());
            return result;
        }

        // 2. 构建飞鹅云添加打印机的参数
        // 格式：打印机编号#打印机识别码#备注名称#流量卡号码
        StringBuilder snlist = new StringBuilder();
        snlist.append(printerInfo.getSn()).append("#");
        snlist.append(printerInfo.getPrinterKey()).append("#");
        snlist.append(printerInfo.getRemark() != null ? printerInfo.getRemark() : "").append("#");
        snlist.append(printerInfo.getCardNum() != null ? printerInfo.getCardNum() : "");

        log.info("调用飞鹅云添加打印机: sn={}, remark={}", printerInfo.getSn(), printerInfo.getRemark());

        // 3. 调用飞鹅云API
        String apiResult = feieyunPrinterUtil.addPrinter(snlist.toString());
        JSONObject apiResponse = JSON.parseObject(apiResult);
        int ret = apiResponse.getIntValue("ret");
        log.info("飞鹅云添加打印机返回: sn={}, ret={}", printerInfo.getSn(), ret);

        if (ret == 0) {
            // API调用成功
            JSONObject data = apiResponse.getJSONObject("data");
            
            // 检查是否添加成功
            if (data != null) {
                com.alibaba.fastjson.JSONArray okList = data.getJSONArray("ok");
                com.alibaba.fastjson.JSONArray noList = data.getJSONArray("no");

                if (okList != null && okList.size() > 0) {
                    // 添加成功，保存到数据库
                    printerInfo.setStatus(PrinterStatusEnum.getByCode(0)); // 初始状态为离线
                    printerInfo.setIsDefault(BooleanEnum.getByCode(0)); // 非默认
                    printerInfo.setEnabled(StatusEnum.getByCode(1)); // 启用
                    printerInfo.setCreatedAt(LocalDateTime.now());
                    printerInfo.setUpdatedAt(LocalDateTime.now());

                    // 如果没有设置打印机类型，默认为小票机
                    if (printerInfo.getPrinterType() == null) {
                        printerInfo.setPrinterType(PrinterTypeEnum.getByCode(1));
                    }
                    if (printerInfo.getPaperWidth() == null) {
                        printerInfo.setPaperWidth(PaperWidthEnum.MM_58);
                    }

                    this.save(printerInfo);

                    result.put("success", true);
                    result.put("msg", "打印机添加成功");
                    result.put("data", printerInfo);

                    log.info("打印机添加成功：{}", printerInfo.getSn());

                } else if (noList != null && noList.size() > 0) {
                    // 添加失败
                    result.put("success", false);
                    result.put("msg", "飞鹅云添加失败：" + noList.getString(0));
                    log.error("飞鹅云添加打印机失败：{}", noList);
                } else {
                    result.put("success", false);
                    result.put("msg", "飞鹅云返回数据异常");
                }
            } else {
                result.put("success", false);
                result.put("msg", "飞鹅云返回数据为空");
            }
        } else {
            // API调用失败
            result.put("success", false);
            result.put("msg", "飞鹅云API错误：" + apiResponse.getString("msg"));
            log.error("飞鹅云API调用失败: sn={}, ret={}, msg={}",
                    printerInfo.getSn(), ret, apiResponse.getString("msg"));
        }

        return result;
    }


    /**
     * 查询打印机状态
     */
    @Override
    public Map<String, Object> queryPrinterStatus(String sn) {
        Map<String, Object> result = new HashMap<>();

        log.info("查询打印机状态：{}", sn);
        String apiResult = feieyunPrinterUtil.queryPrinterStatus(sn);
        log.info("打印机状态返回：{}", apiResult);

        JSONObject apiResponse = JSON.parseObject(apiResult);
        int ret = apiResponse.getIntValue("ret");

        if (ret == 0) {
            String status = apiResponse.getString("data");
            result.put("success", true);
            result.put("sn", sn);
            result.put("status", status);
            result.put("statusCode", convertStatusToCode(status));

            // 更新本地数据库状态
            updateLocalPrinterStatus(sn, status);
        } else {
            result.put("success", false);
            result.put("msg", apiResponse.getString("msg"));
        }

        return result;
    }

    /**
     * 分页查询打印机列表
     */
    @Override
    public Page<PrinterInfo> getPrinterPage(Integer currentPage, Integer pageSize, Long storeId, Integer status) {
        Page<PrinterInfo> page = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<PrinterInfo> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(storeId != null, PrinterInfo::getStoreId, storeId)
                .eq(status != null, PrinterInfo::getStatus, status)
                .orderByDesc(PrinterInfo::getIsDefault)
                .orderByDesc(PrinterInfo::getCreatedAt);

        return this.page(page, wrapper);
    }

    /**
     * 根据门店ID查询打印机列表
     */
    @Override
    public List<PrinterInfo> getPrinterListByStore(Long storeId) {
        return printerInfoMapper.selectByStoreId(storeId);
    }

    /**
     * 根据打印机编号查询
     */
    @Override
    public PrinterInfo getPrinterBySn(String sn) {
        return printerInfoMapper.selectBySn(sn);
    }

    /**
     * 更新打印机信息
     */
    @Override
    public boolean updatePrinter(PrinterInfo printerInfo) {
        storeDataScopeService.checkStoreAccess(printerInfo.getStoreId());
        PrinterInfo existing = this.getById(printerInfo.getId());
        if (existing == null) {
            throw new RuntimeException("打印机不存在");
        }

        // 旧接口省略模板时保留原绑定；换店后必须重新选择适用模板。
        Long targetTemplateId = printerInfo.getTemplateId() == null
                ? existing.getTemplateId() : printerInfo.getTemplateId();
        validateBoundTemplate(targetTemplateId, printerInfo.getStoreId());

        org.hongtu.security.OperationPermission.requireChange("restaurant:printer:status", existing.getEnabled(), printerInfo.getEnabled());
        printerInfo.setUpdatedAt(LocalDateTime.now());
        return this.updateById(printerInfo);
    }

    /** 每台打印机固定绑定一个同门店或商户通用的启用模板。 */
    private void validateBoundTemplate(Long templateId, Long storeId) {
        if (templateId == null) {
            throw new IllegalArgumentException("请选择打印机使用的打印模板");
        }
        PrintTemplate template = printTemplateService.getById(templateId);
        if (template == null || template.getEnabled() != StatusEnum.NORMAL) {
            throw new IllegalArgumentException("打印模板不存在或已禁用");
        }
        if (template.getStoreId() != null && !Objects.equals(template.getStoreId(), storeId)) {
            throw new IllegalArgumentException("打印模板与打印机不属于同一门店");
        }
    }

    /**
     * 删除打印机
     */
    @Override
    public boolean deletePrinter(Long id) {
        PrinterInfo printer = this.getById(id);
        if (printer == null) {
            throw new RuntimeException("打印机不存在");
        }

        // 注意：飞鹅云也提供了删除打印机的API，但这里只删除本地记录
        // 如果需要同时删除飞鹅云上的打印机，可以调用对应API

        return this.removeById(id);
    }


    /**
     * 启用/禁用打印机
     */
    @Override
    public boolean updatePrinterEnabled(Long id, Integer enabled) {
        LambdaUpdateWrapper<PrinterInfo> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PrinterInfo::getId, id)
                .set(PrinterInfo::getEnabled, enabled)
                .set(PrinterInfo::getUpdatedAt, LocalDateTime.now());
        return this.update(wrapper);
    }

    /**
     * 同步所有打印机状态
     */
    @Override
    public Map<String, Object> syncAllPrinterStatus() {
        Map<String, Object> result = new HashMap<>();
        List<PrinterInfo> allPrinters = this.list();

        int successCount = 0;
        int failCount = 0;

        for (PrinterInfo printer : allPrinters) {
            try {
                Map<String, Object> statusResult = queryPrinterStatus(printer.getSn());
                if ((boolean) statusResult.getOrDefault("success", false)) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                log.error("同步打印机状态失败：{}", printer.getSn(), e);
                failCount++;
            }
        }

        result.put("success", true);
        result.put("total", allPrinters.size());
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        return result;
    }


    /**
     * 查询打印机某天的订单统计
     */
    @Override
    public Map<String, Object> queryOrderInfoByDate(String sn, String date) {
        Map<String, Object> result = new HashMap<>();

        log.info("查询打印机订单统计：{}, 日期：{}", sn, date);
        String apiResult = feieyunPrinterUtil.queryOrderInfoByDate(sn, date);
        log.info("订单统计返回：{}", apiResult);

        JSONObject apiResponse = JSON.parseObject(apiResult);
        int ret = apiResponse.getIntValue("ret");

        if (ret == 0) {
            JSONObject data = apiResponse.getJSONObject("data");
            result.put("success", true);
            result.put("sn", sn);
            result.put("date", date);
            result.put("print", data != null ? data.getIntValue("print") : 0);
            result.put("waiting", data != null ? data.getIntValue("waiting") : 0);
        } else {
            result.put("success", false);
            result.put("msg", apiResponse.getString("msg"));
        }

        return result;
    }


    @Override
    public PrinterInfo getDefaultPrinterByStore(Long storeId) {
        LambdaQueryWrapper<PrinterInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PrinterInfo::getStoreId, storeId)
                .eq(PrinterInfo::getEnabled, 1)
                .orderByDesc(PrinterInfo::getIsDefault)
                .orderByDesc(PrinterInfo::getCreatedAt)
                .last("LIMIT 1");

        return this.getOne(queryWrapper);
    }

    /**
     * 获取门店所有启用的打印机列表
     *
     * @param storeId 门店ID
     * @return 启用的打印机列表
     */
    @Override
    public List<PrinterInfo> getEnabledPrintersByStore(Long storeId) {
        LambdaQueryWrapper<PrinterInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PrinterInfo::getStoreId, storeId)
                .eq(PrinterInfo::getEnabled, StatusEnum.getByCode(1))
                .orderByDesc(PrinterInfo::getIsDefault)
                .orderByDesc(PrinterInfo::getCreatedAt);

        return this.list(queryWrapper);
    }

    /**
     * 将状态字符串转换为状态码
     * 
     * @param status 状态字符串（如："在线"、"离线"、"缺纸"）
     * @return 状态码：0-离线，1-在线，2-缺纸
     */
    private int convertStatusToCode(String status) {
        if (status == null) {
            return 0;
        }
        switch (status) {
            case "在线":
            case "在线，工作状态正常。":
                return 1;
            case "缺纸":
            case "在线，工作状态不正常。":
                return 2;
            case "离线。":
            default:
                return 0;
        }
    }

    /**
     * 更新本地数据库中的打印机状态
     */
    private void updateLocalPrinterStatus(String sn, String statusStr) {
        int statusCode = convertStatusToCode(statusStr);

        LambdaUpdateWrapper<PrinterInfo> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(PrinterInfo::getSn, sn)
                .set(PrinterInfo::getStatus, statusCode)
                .set(PrinterInfo::getUpdatedAt, LocalDateTime.now());

        // 如果是在线状态，更新最后在线时间
        if (statusCode == 1) {
            wrapper.set(PrinterInfo::getLastOnlineTime, LocalDateTime.now());
        }

        this.update(wrapper);
        log.info("更新打印机 {} 状态为：{} ({})", sn, statusStr, statusCode);
    }

}
