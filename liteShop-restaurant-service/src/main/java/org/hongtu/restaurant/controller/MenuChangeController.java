package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.MenuChangeRecord;
import org.hongtu.restaurant.VO.MenuChangeBatchRequest;
import org.hongtu.restaurant.VO.MenuChangeDecisionRequest;
import org.hongtu.restaurant.VO.MenuChangeSubmitVO;
import org.hongtu.restaurant.VO.MenuChangeRecordVO;
import org.hongtu.restaurant.service.MenuChangeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单变更审批与发布接口。
 */
@RestController
@RequestMapping("/dish/menu-change")
public class MenuChangeController {

    @Resource
    private MenuChangeService menuChangeService;

    /** 分页查询菜单变更和审批记录。 */
    @GetMapping("/page")
    @SaCheckPermission(value = {"restaurant:dish"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Page<MenuChangeRecordVO>> page(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long dishId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String requestNo) {
        return Result.success(menuChangeService.page(currentPage, pageSize, storeId, dishId, status, requestNo));
    }

    /** 总部按门店分组或门店列表批量发布菜品配置。 */
    @PostMapping("/batch")
    @SaCheckPermission(value = {"restaurant:dish:edit"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<MenuChangeSubmitVO>> batch(@RequestBody MenuChangeBatchRequest request) {
        return Result.success("批量菜单变更已提交",
                menuChangeService.submitBatch(request, StpUtil.getLoginIdAsLong()));
    }

    /** 总部审批通过菜单变更。 */
    @PutMapping("/{id}/approve")
    @SaCheckPermission(value = {"restaurant:dish:edit"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<MenuChangeSubmitVO> approve(@PathVariable Long id,
                                              @RequestBody(required = false) MenuChangeDecisionRequest request) {
        return Result.success("菜单变更审批通过", menuChangeService.approve(id, StpUtil.getLoginIdAsLong(),
                request == null ? null : request.getRemark()));
    }

    /** 总部驳回菜单变更。 */
    @PutMapping("/{id}/reject")
    @SaCheckPermission(value = {"restaurant:dish:edit"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<MenuChangeRecord> reject(@PathVariable Long id,
                                           @RequestBody(required = false) MenuChangeDecisionRequest request) {
        return Result.success("菜单变更已驳回", menuChangeService.reject(id, StpUtil.getLoginIdAsLong(),
                request == null ? null : request.getRemark()));
    }

    /** 发起人或总部撤销尚未生效的菜单变更。 */
    @PutMapping("/{id}/cancel")
    @SaCheckPermission(value = {"restaurant:dish:edit"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<MenuChangeRecord> cancel(@PathVariable Long id) {
        return Result.success("菜单变更已撤销", menuChangeService.cancel(id, StpUtil.getLoginIdAsLong()));
    }
}
