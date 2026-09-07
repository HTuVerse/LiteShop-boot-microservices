package org.hongtu.restaurant.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hongtu.restaurant.DTO.PrintTemplate;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 * 打印模板配置 服务类
 * </p>
 *
 * @author Ht
 * @since 2025-01-27
 */
public interface PrintTemplateService extends IService<PrintTemplate> {

    /**
     * 分页查询打印模板列表
     *
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param storeId 门店ID（可选）
     * @param enabled 是否启用（可选）
     * @return 分页结果
     */
    Page<PrintTemplate> getTemplatePage(Integer currentPage, Integer pageSize,
                                        Long storeId, Integer enabled);

    /**
     * 根据门店ID获取打印模板
     * 优先级：门店专用模板 > 全局默认模板
     *
     * @param storeId 门店ID
     * @return 打印模板
     */
    PrintTemplate getTemplateByStore(Long storeId);

    /**
     * 获取默认模板
     *
     * @return 默认模板
     */
    PrintTemplate getDefaultTemplate();

    /**
     * 创建打印模板
     *
     * @param template 模板信息
     * @return 是否成功
     */
    boolean createTemplate(PrintTemplate template);

    /**
     * 更新打印模板
     *
     * @param template 模板信息
     * @return 是否成功
     */
    boolean updateTemplate(PrintTemplate template);

    /**
     * 删除打印模板
     *
     * @param id 模板ID
     * @return 是否成功
     */
    boolean deleteTemplate(Long id);

    /**
     * 设置默认模板
     *
     * @param id 模板ID
     * @return 是否成功
     */
    boolean setDefaultTemplate(Long id);

    /**
     * 启用/禁用模板
     *
     * @param id 模板ID
     * @param enabled 是否启用
     * @return 是否成功
     */
    boolean updateEnabled(Long id, Integer enabled);

    /**
     * 复制模板
     *
     * @param id 源模板ID
     * @param newTemplateName 新模板名称
     * @return 新模板
     */
    PrintTemplate copyTemplate(Long id, String newTemplateName);
}
