package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.api.util.ResourceBackfillEngine;
import org.hongtu.api.util.ResourceBindingCoordinator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 用户库历史头像的分批预览与精确回填接口。 */
@RestController
@RequiredArgsConstructor
@SaCheckRole("SUPER_ADMIN")
@RequestMapping("/user/admin/resource-reference-backfill")
public class ResourceBackfillController {
    private final JdbcTemplate jdbc;
    private final ResourceBindingCoordinator references;

    /** 平台账号 tenant_id 为空时按0处理；无法确认的外部头像保留原值。 */
    @PostMapping
    public Result<Map<String, Object>> backfill(@RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean apply) {
        var source = new ResourceBackfillEngine.Source("sys_user", "USER", "",
                List.of(new ResourceBackfillEngine.Field("avatar", "avatar", ResourceBackfillEngine.Format.SINGLE)));
        return Result.success(new ResourceBackfillEngine(jdbc, references).run(source, afterId, limit, apply));
    }
}
