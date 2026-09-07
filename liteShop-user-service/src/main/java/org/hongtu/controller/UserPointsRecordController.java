package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import org.hongtu.DTO.UserPointsRecord;
import org.hongtu.Result.Result;
import org.hongtu.enums.PointsSourceEnum;
import org.hongtu.service.UserPointsRecordService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * 用户积分记录 前端控制器
 *
 * @author Ht
 * @since 2025-08-06
 */
@RestController
@RequestMapping("/user-points-record")
public class UserPointsRecordController {

    @Resource
    private UserPointsRecordService userPointsRecordService;

    /**
     * 分页查询当前用户的积分记录
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 积分记录列表
     */
    @GetMapping("/list")
    public Result getCurrentUserPointsRecords(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer source
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        return userPointsRecordService.getUserPointsRecords(userId, pageNum, pageSize,PointsSourceEnum.getByCode(source));
    }

    /**
     * 获取当前用户的积分统计信息
     * @return 积分统计信息
     */
    @GetMapping("/statistics")
    public Result getCurrentUserPointsStatistics() {
        Long userId = StpUtil.getLoginIdAsLong();
        return userPointsRecordService.getUserPointsStatistics(userId);
    }

    /**
     * 分页查询指定用户的积分记录（管理员权限）
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 积分记录列表
     */
    @GetMapping("/admin/list")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:user"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result getUserPointsRecords(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer source) {
        return userPointsRecordService.getUserPointsRecords(userId, pageNum, pageSize,PointsSourceEnum.getByCode(source));
    }


    /**
     * 获取指定用户的积分统计信息（管理员权限）
     * @param userId 用户ID
     * @return 积分统计信息
     */
    @GetMapping("/admin/statistics")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:user"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result getUserPointsStatistics(@RequestParam Long userId) {
        return userPointsRecordService.getUserPointsStatistics(userId);
    }

    /**
     * 根据ID删除积分记录（管理员权限）
     * @param id 记录ID
     * @return 操作结果
     */
    @DeleteMapping("/admin/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:user:points-delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result deletePointsRecord(@PathVariable Long id) {
        boolean removed = userPointsRecordService.removeById(id);
        return removed ? Result.success("删除成功") : Result.error("删除失败");
    }
}
