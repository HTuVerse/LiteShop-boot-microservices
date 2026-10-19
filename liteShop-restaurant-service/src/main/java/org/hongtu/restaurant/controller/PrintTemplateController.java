package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.PrintTemplate;
import org.hongtu.restaurant.service.PrintTemplateService;
import org.hongtu.restaurant.utils.PrintContentBuilder;
import org.springframework.web.bind.annotation.*;

/**
 * 打印模板配置管理接口
 *
 * @author hongtu
 */
@Slf4j
@RestController
@RequestMapping("/printTemplateApi")
public class PrintTemplateController {

    @Resource
    private PrintTemplateService printTemplateService;

    /** 使用当前编辑中的模板生成演示小票，与正式打印共用内容构建器。 */
    @PostMapping("/preview")
    @SaCheckPermission(value = {"operations:templates", "restaurant:printer"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<String> preview(@RequestBody PrintTemplate template) {
        return Result.success(PrintContentBuilder.buildSampleContent(template));
    }

    /**
     * 分页查询打印模板列表
     *
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param storeId 门店ID（可选）
     * @param enabled 是否启用（可选）
     * @return 分页结果
     */
    @GetMapping("/page")
    @SaCheckPermission(value = {"operations:templates", "restaurant:printer", "restaurant:order", "operations:direct-cashier", "operations:table-cashier"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Page<PrintTemplate>> getTemplatePage(
            @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "storeId", required = false) Long storeId,
            @RequestParam(value = "enabled", required = false) Integer enabled) {

        log.info("分页查询打印模板列表：page={}, size={}, storeId={}, enabled={}",
                currentPage, pageSize, storeId, enabled);

        
            Page<PrintTemplate> page = printTemplateService.getTemplatePage(currentPage, pageSize, storeId, enabled);
            return Result.success(page);
        
    }

    /**
     * 根据ID查询打印模板
     *
     * @param id 模板ID
     * @return 模板信息
     */
    @GetMapping("/getTemplateById")
    @SaCheckPermission(value = {"operations:templates", "restaurant:printer", "restaurant:order", "operations:direct-cashier", "operations:table-cashier"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<PrintTemplate> getTemplateById(@RequestParam("id") Long id) {
        log.info("根据ID查询打印模板：id={}", id);

        
            PrintTemplate template = printTemplateService.getById(id);
            if (template != null) {
                return Result.success(template);
            } else {
                return Result.error("模板不存在");
            }
        
    }

    /**
     * 根据门店ID获取打印模板
     *
     * @param storeId 门店ID
     * @return 模板信息
     */
    @GetMapping("/getTemplateByStore")
    @SaCheckPermission(value = {"operations:templates", "restaurant:printer", "restaurant:order", "operations:direct-cashier", "operations:table-cashier"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<PrintTemplate> getTemplateByStore(
            @RequestParam("storeId") Long storeId) {

        log.info("根据门店获取模板：storeId={}", storeId);

        
            PrintTemplate template = printTemplateService.getTemplateByStore(storeId);
            return Result.success(template);
        
    }

    /**
     * 获取默认模板
     *
     * @return 默认模板
     */
    @GetMapping("/getDefaultTemplate")
    @SaCheckPermission(value = {"operations:templates", "restaurant:printer", "restaurant:order", "operations:direct-cashier", "operations:table-cashier"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<PrintTemplate> getDefaultTemplate() {
        log.info("获取默认打印模板");

        
            PrintTemplate template = printTemplateService.getDefaultTemplate();
            if (template != null) {
                return Result.success(template);
            } else {
                return Result.error("默认模板不存在");
            }
        
    }

    /**
     * 创建打印模板
     *
     * @param template 模板信息
     * @return 创建结果
     */
    @PostMapping("/create")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"operations:templates:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> createTemplate(@RequestBody PrintTemplate template) {
        log.info("创建打印模板：{}", template.getTemplateName());

        
            boolean success = printTemplateService.createTemplate(template);
            return success ? Result.success(true) : Result.error("创建失败");
        
    }

    /**
     * 更新打印模板
     *
     * @param template 模板信息
     * @return 更新结果
     */
    @PutMapping("/update")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"operations:templates:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updateTemplate(@RequestBody PrintTemplate template) {
        log.info("更新打印模板：id={}", template.getId());

        
            boolean success = printTemplateService.updateTemplate(template);
            return success ? Result.success(true) : Result.error("更新失败");
        
    }

    /**
     * 删除打印模板
     *
     * @param id 模板ID
     * @return 删除结果
     */
    @DeleteMapping("/delete/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"operations:templates:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> deleteTemplate(@PathVariable("id") Long id) {
        log.info("删除打印模板：id={}", id);

        
            boolean success = printTemplateService.deleteTemplate(id);
            return success ? Result.success(true) : Result.error("删除失败");
        
    }

    /**
     * 设置默认模板
     *
     * @param id 模板ID
     * @return 操作结果
     */
    @PutMapping("/setDefault")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"operations:templates:default"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> setDefaultTemplate(@RequestParam("id") Long id) {
        log.info("设置默认模板：id={}", id);

        
            boolean success = printTemplateService.setDefaultTemplate(id);
            return success ? Result.success(true) : Result.error("操作失败");
        
    }

    /**
     * 启用/禁用模板
     *
     * @param id 模板ID
     * @param enabled 是否启用：0-禁用，1-启用
     * @return 操作结果
     */
    @PutMapping("/updateEnabled")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"operations:templates:status"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updateEnabled(
            @RequestParam("id") Long id,
            @RequestParam("enabled") Integer enabled) {
        log.info("更新模板启用状态：id={}, enabled={}", id, enabled);

        
            boolean success = printTemplateService.updateEnabled(id, enabled);
            return success ? Result.success(true) : Result.error("操作失败");
        
    }

    /**
     * 复制模板
     *
     * @param id 源模板ID
     * @param newTemplateName 新模板名称
     * @return 新模板
     */
    @PostMapping("/copy")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"operations:templates:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<PrintTemplate> copyTemplate(
            @RequestParam("id") Long id,
            @RequestParam("newTemplateName") String newTemplateName) {
        log.info("复制模板：id={}, newName={}", id, newTemplateName);

        
            PrintTemplate newTemplate = printTemplateService.copyTemplate(id, newTemplateName);
            if (newTemplate != null) {
                return Result.success(newTemplate);
            } else {
                return Result.error("复制失败");
            }
        
    }
}
