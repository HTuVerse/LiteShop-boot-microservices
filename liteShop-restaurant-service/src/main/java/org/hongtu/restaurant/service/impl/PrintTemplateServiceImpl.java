package org.hongtu.restaurant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.StatusEnum;
import org.hongtu.restaurant.DAO.PrintTemplateMapper;
import org.hongtu.restaurant.DTO.PrintTemplate;
import org.hongtu.restaurant.enums.PaperWidthEnum;
import org.hongtu.restaurant.service.PrintTemplateService;
import org.hongtu.restaurant.service.StoreDataScopeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 打印模板配置 服务实现类
 * </p>
 *
 * @author Ht
 * @since 2025-01-27
 */
@Slf4j
@Service
public class PrintTemplateServiceImpl extends ServiceImpl<PrintTemplateMapper, PrintTemplate> implements PrintTemplateService {

    @Resource
    private StoreDataScopeService storeDataScopeService;

    @Override
    public Page<PrintTemplate> getTemplatePage(Integer currentPage, Integer pageSize,
                                               Long storeId, Integer enabled) {
        Page<PrintTemplate> page = new Page<>(currentPage, pageSize);

        LambdaQueryWrapper<PrintTemplate> queryWrapper = new LambdaQueryWrapper<>();

        if (storeId != null) {
            queryWrapper.eq(PrintTemplate::getStoreId, storeId);
        }
        if (enabled != null) {
            queryWrapper.eq(PrintTemplate::getEnabled, enabled);
        }

        queryWrapper.orderByDesc(PrintTemplate::getIsDefault)
                .orderByDesc(PrintTemplate::getCreatedAt);

        return this.page(page, queryWrapper);
    }

    @Override
    public PrintTemplate getTemplateByStore(Long storeId) {
        LambdaQueryWrapper<PrintTemplate> queryWrapper = new LambdaQueryWrapper<>();

        // 优先查找门店专用模板
        if (storeId != null) {
            queryWrapper.eq(PrintTemplate::getStoreId, storeId)
                    .eq(PrintTemplate::getEnabled, StatusEnum.NORMAL)
                    .orderByDesc(PrintTemplate::getIsDefault)
                    .orderByDesc(PrintTemplate::getCreatedAt)
                    .last("LIMIT 1");
            PrintTemplate template = this.getOne(queryWrapper);
            if (template != null) {
                log.info("使用门店专用模板：{}", template.getTemplateName());
                return template;
            }
        }

        // 使用全局默认模板
        PrintTemplate defaultTemplate = getDefaultTemplate();
        if (defaultTemplate != null) {
            log.info("使用全局默认模板：{}", defaultTemplate.getTemplateName());
            return defaultTemplate;
        }

        // 如果没有任何模板，返回内置默认配置
        log.warn("未找到任何打印模板，使用内置默认配置");
        return createBuiltInDefaultTemplate();
    }

    @Override
    public PrintTemplate getDefaultTemplate() {
        LambdaQueryWrapper<PrintTemplate> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PrintTemplate::getIsDefault, BooleanEnum.YES)
                .eq(PrintTemplate::getEnabled, StatusEnum.NORMAL)
                .isNull(PrintTemplate::getStoreId)
                .last("LIMIT 1");
        return this.getOne(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createTemplate(PrintTemplate template) {
        storeDataScopeService.checkStoreAccess(template.getStoreId());
        if (storeDataScopeService.requiresStoreRestriction() && template.getStoreId() == null) {
            throw new SecurityException("门店管理员无权创建全局打印模板");
        }
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());

        // 如果设置为默认模板，需要先取消其他默认模板
        if (template.getIsDefault() != null && template.getIsDefault() == BooleanEnum.YES) {
            cancelOtherDefaultTemplates(template.getStoreId());
        }

        return this.save(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTemplate(PrintTemplate template) {
        PrintTemplate existing = template.getId() == null ? null : this.getById(template.getId());
        if (existing == null) {
            return false;
        }
        org.hongtu.security.OperationPermission.requireChange("operations:templates:status", existing.getEnabled(), template.getEnabled());
        org.hongtu.security.OperationPermission.requireChange("operations:templates:default", existing.getIsDefault(), template.getIsDefault());
        Long targetStoreId = template.getStoreId() == null ? existing.getStoreId() : template.getStoreId();
        storeDataScopeService.checkStoreAccess(targetStoreId);
        if (storeDataScopeService.requiresStoreRestriction() && targetStoreId == null) {
            throw new SecurityException("门店管理员无权修改为全局打印模板");
        }
        template.setUpdatedAt(LocalDateTime.now());

        // 如果设置为默认模板，需要先取消其他默认模板
        if (template.getIsDefault() != null && template.getIsDefault() == BooleanEnum.YES) {
            cancelOtherDefaultTemplates(template.getStoreId());
        }

        return this.updateById(template);
    }

    @Override
    public boolean deleteTemplate(Long id) {
        PrintTemplate template = this.getById(id);
        if (template == null) {
            return false;
        }

        // 不允许删除默认模板
        if (template.getIsDefault() != null && template.getIsDefault() == BooleanEnum.YES) {
            throw new RuntimeException("不能删除默认模板");
        }

        return this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setDefaultTemplate(Long id) {
        PrintTemplate template = this.getById(id);
        if (template == null) {
            return false;
        }

        // 取消其他默认模板
        cancelOtherDefaultTemplates(template.getStoreId());

        // 设置当前模板为默认
        template.setIsDefault(BooleanEnum.YES);
        template.setUpdatedAt(LocalDateTime.now());
        return this.updateById(template);
    }

    @Override
    public boolean updateEnabled(Long id, Integer enabled) {
        PrintTemplate template = this.getById(id);
        if (template == null) {
            return false;
        }

        template.setEnabled(StatusEnum.getByCode(enabled));
        template.setUpdatedAt(LocalDateTime.now());
        return this.updateById(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrintTemplate copyTemplate(Long id, String newTemplateName) {
        PrintTemplate sourceTemplate = this.getById(id);
        if (sourceTemplate == null) {
            return null;
        }

        PrintTemplate newTemplate = new PrintTemplate();
        // 复制所有配置
        newTemplate.setTemplateName(newTemplateName);
        newTemplate.setStoreId(sourceTemplate.getStoreId());
        newTemplate.setPaperWidth(sourceTemplate.getPaperWidth());
        newTemplate.setShowLogo(sourceTemplate.getShowLogo());
        newTemplate.setLogoUrl(sourceTemplate.getLogoUrl());
        newTemplate.setStoreNameStyle(sourceTemplate.getStoreNameStyle());
        newTemplate.setShowAddress(sourceTemplate.getShowAddress());
        newTemplate.setShowPhone(sourceTemplate.getShowPhone());
        newTemplate.setShowBusinessHours(sourceTemplate.getShowBusinessHours());
        newTemplate.setOrderInfoFields(sourceTemplate.getOrderInfoFields());
        newTemplate.setDishListStyle(sourceTemplate.getDishListStyle());
        newTemplate.setShowDishImage(sourceTemplate.getShowDishImage());
        newTemplate.setShowDishSpec(sourceTemplate.getShowDishSpec());
        newTemplate.setShowDishNote(sourceTemplate.getShowDishNote());
        newTemplate.setShowOrderNote(sourceTemplate.getShowOrderNote());
        newTemplate.setShowPaymentInfo(sourceTemplate.getShowPaymentInfo());
        newTemplate.setFooterText(sourceTemplate.getFooterText());
        newTemplate.setShowQrcode(sourceTemplate.getShowQrcode());
        newTemplate.setQrcodeTemplate(sourceTemplate.getQrcodeTemplate());
        newTemplate.setQrcodeSize(sourceTemplate.getQrcodeSize());
        newTemplate.setFontSize(sourceTemplate.getFontSize());
        newTemplate.setPrintDensity(sourceTemplate.getPrintDensity());
        newTemplate.setPrintCopies(sourceTemplate.getPrintCopies());
        newTemplate.setIsDefault(BooleanEnum.NO); // 复制的模板不设为默认
        newTemplate.setEnabled(StatusEnum.NORMAL);
        newTemplate.setRemark("从模板【" + sourceTemplate.getTemplateName() + "】复制");
        newTemplate.setCreatedAt(LocalDateTime.now());
        newTemplate.setUpdatedAt(LocalDateTime.now());

        this.save(newTemplate);
        return newTemplate;
    }

    /**
     * 取消其他默认模板
     */
    private void cancelOtherDefaultTemplates(Long storeId) {
        LambdaQueryWrapper<PrintTemplate> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PrintTemplate::getIsDefault, BooleanEnum.YES);

        if (storeId != null) {
            queryWrapper.eq(PrintTemplate::getStoreId, storeId);
        } else {
            queryWrapper.isNull(PrintTemplate::getStoreId);
        }

        PrintTemplate updateTemplate = new PrintTemplate();
        updateTemplate.setIsDefault(BooleanEnum.NO);
        updateTemplate.setUpdatedAt(LocalDateTime.now());

        this.update(updateTemplate, queryWrapper);
    }

    /**
     * 创建内置默认模板配置
     */
    private PrintTemplate createBuiltInDefaultTemplate() {
        PrintTemplate template = new PrintTemplate();
        template.setTemplateName("内置默认模板");
        template.setPaperWidth(PaperWidthEnum.MM_58);
        template.setShowLogo(StatusEnum.DISABLED);
        template.setStoreNameStyle(2); // 加粗+居中
        template.setShowAddress(StatusEnum.NORMAL);
        template.setShowPhone(StatusEnum.NORMAL);
        template.setShowBusinessHours(StatusEnum.DISABLED);
        template.setOrderInfoFields("[\"orderNo\",\"tableName\",\"orderType\",\"createdAt\"]");
        template.setDishListStyle(0); // 简约样式
        template.setShowDishImage(StatusEnum.DISABLED);
        template.setShowDishSpec(StatusEnum.NORMAL);
        template.setShowDishNote(StatusEnum.NORMAL);
        template.setShowOrderNote(StatusEnum.NORMAL);
        template.setShowPaymentInfo(StatusEnum.NORMAL);
        template.setFooterText("谢谢惠顾，欢迎再次光临！");
        template.setShowQrcode(StatusEnum.NORMAL);
        template.setQrcodeTemplate("http://yourdomain.com/order/{orderNo}");
        template.setQrcodeSize(2);
        template.setFontSize(0);
        template.setPrintDensity(8);
        template.setPrintCopies(1);
        template.setIsDefault(BooleanEnum.YES);
        template.setEnabled(StatusEnum.NORMAL);
        return template;
    }
}
