package org.hongtu.restaurant.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.AdminOpenTableRequest;
import org.hongtu.restaurant.VO.ScanTableRequest;
import org.hongtu.restaurant.VO.TableSessionVO;
import org.hongtu.restaurant.service.TableSessionService;
import org.hongtu.restaurant.security.TableSessionTokenService;
import org.hongtu.tenant.TenantContext;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 餐桌会话表 前端控制器
 *
 * @author Ht
 * @since 2025-09-19
 */
@RestController
@RequestMapping("/table-session")
public class TableSessionController {

    @Resource
    private TableSessionService tableSessionService;

    @Resource
    private TableSessionTokenService tableSessionTokenService;

    @Resource
    private org.hongtu.restaurant.service.TableInfoService tableInfoService;

    /**
     * 扫码加入餐桌（推荐使用）
     * 自动判断是开桌还是加入现有会话
     *
     * @param request 扫码请求
     * @return 会话信息
     */
    @PostMapping("/scan")
    @SaIgnore
    public Result<TableSessionVO> scanAndJoin(@RequestBody ScanTableRequest request) {
        
            if (request.getTableId() == null) {
                return Result.error("桌位ID不能为空");
            }
            org.hongtu.restaurant.DTO.TableInfo table = tableInfoService.getById(request.getTableId());
            if (table == null) {
                return Result.error("桌位不存在");
            }
            tableSessionTokenService.verifyForTable(
                    request.getScanToken(), request.getTableId(), TenantContext.getTenantId(),
                    table.getStoreId(), table.getScanTokenId());
            TableSessionVO session = tableSessionService.scanAndJoinTable(request);
            session.setSessionToken(tableSessionTokenService.issue(TenantContext.getTenantId(), session.getSessionNo()));
            return Result.success("加入成功", session);
        
    }

    /**
     * 管理员开桌/加入餐桌（收银前台专用）
     * 自动判断是开桌还是加入现有会话
     * 与扫码接口的区别：需要管理员权限，记录操作人信息，支持备注
     *
     * @param request 管理员开桌请求
     * @return 会话信息
     */
    @PostMapping("/admin/open")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:table:open", "operations:table-cashier:open", "operations:console:open"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<TableSessionVO> adminOpenTable(@RequestBody AdminOpenTableRequest request) {
        
            // 参数校验
            if (request.getTableId() == null) {
                return Result.error("桌位ID不能为空");
            }
            request.setOperatorId(cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong());
            if (request.getOperatorId() == null) {
                return Result.error("操作人ID不能为空");
            }

            // 转换为扫码请求格式，复用现有逻辑
            ScanTableRequest scanRequest = new ScanTableRequest();
            scanRequest.setTableId(request.getTableId());
            scanRequest.setReservationId(request.getReservationId());
            scanRequest.setPeopleCount(request.getPeopleCount());
            scanRequest.setUserId(request.getOperatorId());
            scanRequest.setUserName(request.getOperatorName());
            scanRequest.setRemark(request.getRemark());

            TableSessionVO session = tableSessionService.scanAndJoinTable(scanRequest);
            session.setSessionToken(tableSessionTokenService.issue(TenantContext.getTenantId(), session.getSessionNo()));

            return Result.success("操作成功", session);
        
    }

    /**
     * 更新就餐人数
     * @param sessionNo 会话编号
     * @param peopleCount 新的就餐人数
     * @return 更新后的会话信息
     */
    @PutMapping("/update-people-count")
    @SaIgnore
    public Result<TableSessionVO> updatePeopleCount(
            @RequestParam String sessionNo,
            @RequestParam Integer peopleCount,
            @RequestHeader(TableSessionTokenService.HEADER_NAME) String sessionToken
    ) {
        
            if (!isTableOperator()) {
                tableSessionTokenService.verifyForSession(sessionToken, sessionNo, TenantContext.getTenantId());
            }
            if (peopleCount == null || peopleCount < 1) {
                return Result.error("就餐人数必须大于0");
            }
            if (peopleCount > 30) {
                return Result.error("就餐人数不能超过30人");
            }

            TableSessionVO session = tableSessionService.updatePeopleCount(sessionNo, peopleCount);
            return Result.success("更新成功", session);
        
    }


    /**
     * 根据会话编号查询会话详情
     * @param sessionNo 会话编号
     * @return 会话详情
     */
    @GetMapping("/detail/{sessionNo}")
    @SaIgnore
    public Result<TableSessionVO> getSessionDetail(
            @PathVariable String sessionNo,
            @RequestHeader(value = TableSessionTokenService.HEADER_NAME, required = false) String sessionToken) {
        if (!isTableOperator()) {
            tableSessionTokenService.verifyForSession(sessionToken, sessionNo, TenantContext.getTenantId());
        }
        TableSessionVO session = tableSessionService.getSessionDetailByNo(sessionNo);
        if (session != null) {
            return Result.success("查询成功", session);
        } else {
            return Result.error("会话不存在");
        }
    }

    /**
     * 根据桌位ID获取当前会话（用于扫码判断是开桌还是加入）
     * @param tableId 桌位ID
     * @return 会话信息
     */
    @GetMapping("/checkActive")
    @SaCheckPermission(value = {"restaurant:table", "operations:table-cashier", "operations:console"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<TableSessionVO> getActiveSession(@RequestParam Long tableId) {
        var session = tableSessionService.getActiveSessionByTableId(tableId);
        if (session != null) {
            TableSessionVO vo = tableSessionService.getSessionDetailById(session.getId());
            return Result.success("该桌位已有进行中的会话", vo);
        } else {
            return Result.success("该桌位可以开桌");
        }
    }

    /**
     * 旧版结束会话接口已停用。该接口无法验证订单应付金额，结算必须走统一收银接口。
     * @param sessionNo 会话编号
     * @param paidAmount 客户端提交金额（不再接受）
     * @return 停用提示
     */
    @Deprecated
    @PutMapping("/end")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:table:settle", "operations:table-cashier:settle", "operations:console:settle"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> endSession(
            @RequestParam String sessionNo,
            @RequestParam BigDecimal paidAmount
    ) {
        return Result.error("该结账接口已停用，请使用统一桌位收银接口");
    }

    /**
     * 获取所有进行中的会话（后台管理用）
     * @param storeId 门店ID（可选）
     * @return 会话列表
     */
    @GetMapping("/active/list")
    @SaCheckPermission(value = {"restaurant:table", "operations:table-cashier", "operations:console"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<TableSessionVO>> getActiveSessionList(
            @RequestParam(required = false) Long storeId
    ) {
        List<TableSessionVO> sessions = tableSessionService.getActiveSessionList(storeId);
        return Result.success("查询成功", sessions);
    }

    /**
     * 分页查询会话列表（后台管理用）
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param storeId 门店ID
     * @param status 状态：1-进行中，2-已结束
     * @return 分页会话列表
     */
    @GetMapping("/pageList")
    @SaCheckPermission(value = {"restaurant:table", "operations:table-cashier", "operations:console"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Page<TableSessionVO>> getSessionPage(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Integer status
    ) {
        Page<TableSessionVO> page = new Page<>(currentPage, pageSize);
        Page<TableSessionVO> sessionPage = tableSessionService.getSessionPage(storeId, status, page);
        return Result.success("查询成功", sessionPage);
    }

    /**
     * 一键清除所有会话，恢复餐桌状态
     * 用于每日打烊或系统重置时使用
     * @param storeId 门店ID（可选，为空时清除所有门店）
     * @return 清除的会话数量
     */
    @PostMapping("/clearAll")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:table:clear", "operations:table-cashier:clear", "operations:console:clear"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> clearAllSessions(
            @RequestParam(required = false) Long storeId
    ) {
        
            int clearedCount = tableSessionService.clearAllSessions(storeId);
            Map<String, Object> data = new HashMap<>();
            data.put("clearedSessionCount", clearedCount);
            data.put("storeId", storeId);
            
            String message = storeId != null 
                    ? String.format("门店[%d]会话清除完成，共清除%d个会话", storeId, clearedCount)
                    : String.format("所有会话清除完成，共清除%d个会话", clearedCount);
            
            return Result.success(message, data);
        
    }

    /**
     * 批量清除指定餐桌的会话，恢复餐桌状态
     * @param tableIds 餐桌ID列表
     * @return 清除的会话数量
     */
    @PostMapping("/clearByTables")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:table:clear", "operations:table-cashier:clear", "operations:console:clear"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> clearSessionsByTables(
            @RequestBody List<Long> tableIds
    ) {
        
            if (tableIds == null || tableIds.isEmpty()) {
                return Result.error("餐桌ID列表不能为空");
            }
            
            int clearedCount = tableSessionService.clearSessionsByTableIds(tableIds);
            Map<String, Object> data = new HashMap<>();
            data.put("clearedSessionCount", clearedCount);
            data.put("tableIds", tableIds);
            
            return Result.success(
                    String.format("指定餐桌会话清除完成，共清除%d个会话", clearedCount), 
                    data
            );
        
    }

    private boolean isTableOperator() {
        try {
            return StpUtil.isLogin() && (StpUtil.hasRole("SUPER_ADMIN") || List.of(
                    "restaurant:table", "operations:table-cashier", "operations:console")
                    .stream().anyMatch(StpUtil::hasPermission));
        } catch (Exception ignored) {
            return false;
        }
    }

}
