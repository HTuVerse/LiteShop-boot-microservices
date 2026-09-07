package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.TableReservationRequest;
import org.hongtu.restaurant.VO.TableReservationVO;
import org.hongtu.restaurant.service.TableReservationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/table-reservation")
@SaCheckPermission(value = {"restaurant:table:status", "operations:table-cashier:status", "operations:console:status", "operations:table-reservation"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
public class TableReservationController {
    @Resource private TableReservationService tableReservationService;

    @GetMapping("/pageList")
    public Result<Page<TableReservationVO>> pageList(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String statusGroup,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success("查询预约记录成功", tableReservationService.getPage(
                storeId, tableId, phone, status, statusGroup, startTime, endTime, currentPage, pageSize));
    }

    @PostMapping("/create")
    public Result<TableReservationVO> create(@RequestBody TableReservationRequest request) {
        return Result.success("预约成功", tableReservationService.createReservation(request, StpUtil.getLoginIdAsLong()));
    }

    @PutMapping("/update")
    public Result<TableReservationVO> update(@RequestBody TableReservationRequest request) {
        return Result.success("预约更新成功", tableReservationService.updateReservation(request));
    }

    @PostMapping("/{id}/cancel")
    public Result<Boolean> cancel(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return Result.success("预约已取消", tableReservationService.cancelReservation(id, reason));
    }

    @PostMapping("/{id}/no-show")
    public Result<Boolean> noShow(@PathVariable Long id) {
        return Result.success("已标记爽约", tableReservationService.markNoShow(id));
    }
}
