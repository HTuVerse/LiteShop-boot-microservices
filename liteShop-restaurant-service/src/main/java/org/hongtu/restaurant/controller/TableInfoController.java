package org.hongtu.restaurant.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.TableInfo;
import org.hongtu.restaurant.VO.TableInfoVO;
import org.hongtu.restaurant.service.TableInfoService;
import org.hongtu.restaurant.service.TableSessionService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 餐桌信息表 前端控制器
 *
 * @author Ht
 * @since 2025-09-19
 */
@RestController
@RequestMapping("/table-info")
public class TableInfoController {

    @Resource
    TableInfoService tableInfoService;

    /**
     * 根据ID查询桌位详情
     * @param id 桌位ID
     * @return 桌位详情
     */
    @GetMapping("/getDetail")
    @SaIgnore
    public Result<TableInfoVO> getTableDetail(@RequestParam Long id) {
        TableInfoVO tableInfo = tableInfoService.getTableDetail(id);
        if (tableInfo != null) {
            return Result.success("查询桌位详情成功", tableInfo);
        } else {
            return Result.error("查询桌位详情失败");
        }
    }

    /**
     * 添加桌位
     * @param tableInfo 桌位信息
     * @return 结果
     */
    @PostMapping("/add")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:table:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> addTable(@RequestBody TableInfo tableInfo) {
        boolean result = tableInfoService.addTable(tableInfo);
        if (result) {
            return Result.success("添加桌位成功", result);
        } else {
            return Result.error("添加桌位失败");
        }
    }

    /**
     * 更新桌位信息
     * @param tableInfo 桌位信息
     * @return 结果
     */
    @PutMapping("/update")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:table:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updateTable(@RequestBody TableInfo tableInfo) {
        boolean result = tableInfoService.updateTable(tableInfo);
        if (result) {
            return Result.success("更新桌位成功", result);
        } else {
            return Result.error("更新桌位失败");
        }
    }

    /**
     * 删除桌位
     * @param id 桌位ID
     * @return 结果
     */
    @DeleteMapping("/delete/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:table:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> deleteTable(@PathVariable Long id) {
        boolean result = tableInfoService.deleteTable(id);
        if (result) {
            return Result.success("删除桌位成功", result);
        } else {
            return Result.error("删除桌位失败");
        }
    }

    /**
     * 批量删除桌位
     * @param ids 桌位ID列表
     * @return 结果
     */
    @DeleteMapping("/batchDelete")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:table:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> batchDeleteTable(@RequestBody List<Long> ids) {
        boolean result = tableInfoService.batchDeleteTable(ids);
        if (result) {
            return Result.success("批量删除桌位成功", result);
        } else {
            return Result.error("批量删除桌位失败");
        }
    }

    /**
     * 分页查询桌位列表
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param storeId 门店ID
     * @param tableNumber 桌位编号
     * @param tableName 桌位名称
     * @param status 状态
     * @return 分页桌位列表
     */
    @GetMapping("/pageList")
    @SaCheckPermission(value = {"restaurant:table", "operations:table-cashier", "operations:console", "operations:table-reservation"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Page<TableInfoVO>> getTablePage(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String tableNumber,
            @RequestParam(required = false) String tableName,
            @RequestParam(required = false) Integer status
    ) {
        // 创建分页对象
        Page<TableInfo> page = new Page<>(currentPage, pageSize);
        // 分页查询
        Page<TableInfoVO> tablePage = tableInfoService.getTablePage(storeId, tableNumber, tableName, status, page);

        return Result.success("查询桌位列表成功", tablePage);
    }

    /**
     * 更新桌位状态
     * @param id 桌位ID
     * @param status 状态：0-空闲，1-使用中，2-预留，3-停用
     * @return 结果
     */
    @PutMapping("/updateStatus")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:table:status", "operations:table-cashier:status", "operations:console:status"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updateTableStatus(
            @RequestParam Long id,
            @RequestParam Integer status
    ) {
        boolean result = tableInfoService.updateTableStatus(id, status);
        if (result) {
            return Result.success("更新桌位状态成功", result);
        } else {
            return Result.error("更新桌位状态失败");
        }
    }

}
