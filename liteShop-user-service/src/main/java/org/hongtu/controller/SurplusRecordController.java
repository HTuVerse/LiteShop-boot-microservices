package org.hongtu.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.hongtu.Result.Result;
import org.hongtu.service.SurplusRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * 余额变动历史表 前端控制器
 * 仅允许用户查询自己的余额变动明细，且仅支持查询最近1年数据。
 *
 * @author Ht
 * @since 2025-08-25
 */
@RestController
@RequestMapping("/surplus-record")
public class SurplusRecordController {

    @Resource
    private SurplusRecordService surplusRecordService;

    /**
     * 分页查询当前登录用户的余额变动明细（最近1年）
     * @param pageNum 页码，默认1
     * @param pageSize 每页条数，默认10
     * @param type 变动类型（可选）：1-用户充值 2-管理员充值 3-退款 4-消费
     */
    @GetMapping("/list")
    public Result getMyList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer type
    ) {
        Long userId = StpUtil.getLoginIdAsLong();
        return surplusRecordService.getMyRecordPage(userId, pageNum, pageSize, type);
    }
}
