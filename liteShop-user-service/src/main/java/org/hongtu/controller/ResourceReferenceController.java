package org.hongtu.controller;

import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 内部检查用户头像对资源 URL 的引用。 */
@RestController
@RequiredArgsConstructor
public class ResourceReferenceController {
    private final JdbcTemplate jdbc;

    /** 文件删除和转私有前检查全部用户头像。 */
    @GetMapping("/inner/resource-reference")
    public Result<Boolean> used(@RequestParam String url) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE avatar = ?", Integer.class, url);
        return Result.success(count != null && count > 0);
    }
}
