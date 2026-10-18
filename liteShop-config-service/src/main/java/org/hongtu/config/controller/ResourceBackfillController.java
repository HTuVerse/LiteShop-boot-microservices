package org.hongtu.config.controller;

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

/** 配置库历史轮播图片的分批预览与精确回填接口。 */
@RestController
@RequiredArgsConstructor
@SaCheckRole("SUPER_ADMIN")
@RequestMapping("/config/admin/resource-reference-backfill")
public class ResourceBackfillController {
    private final JdbcTemplate jdbc;
    private final ResourceBindingCoordinator references;

    /** 默认只预览；确认备份后按 nextId 重复调用即可续跑。 */
    @PostMapping
    public Result<Map<String, Object>> backfill(@RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean apply) {
        var source = new ResourceBackfillEngine.Source("sys_banner", "BANNER", "",
                List.of(new ResourceBackfillEngine.Field("image_url", "imageUrl",
                        ResourceBackfillEngine.Format.SINGLE)));
        return Result.success(new ResourceBackfillEngine(jdbc, references).run(source, afterId, limit, apply));
    }
}
