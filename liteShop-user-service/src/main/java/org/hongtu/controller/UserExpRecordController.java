package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import org.hongtu.Result.Result;
import org.hongtu.service.UserExpRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 会员经验成长明细查询接口。 */
@RestController
@RequestMapping("/user-exp-record")
public class UserExpRecordController {
    private final UserExpRecordService records;

    public UserExpRecordController(UserExpRecordService records) { this.records = records; }

    /** 当前登录会员分页查询自己的经验明细。 */
    @GetMapping("/list")
    public Result list(@RequestParam(defaultValue = "1") Integer pageNum,
                       @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(records.page(StpUtil.getLoginIdAsLong(), pageNum, pageSize));
    }

    /** 管理员查看指定会员的经验明细。 */
    @GetMapping("/admin/list")
    @SaCheckPermission(value = "system:user", orRole = "SUPER_ADMIN")
    public Result adminList(@RequestParam Long userId,
                            @RequestParam(defaultValue = "1") Integer pageNum,
                            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(records.page(userId, pageNum, pageSize));
    }
}
