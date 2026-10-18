package org.hongtu.config.controller;

import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 内部检查 Banner 与网站配置对资源 URL 的引用。 */
@RestController
@RequiredArgsConstructor
public class ResourceReferenceController {
    private final JdbcTemplate jdbc;

    /** 删除或隐藏公开资源前阻止破坏仍在使用的图片。 */
    @GetMapping("/inner/resource-reference")
    public Result<Boolean> used(@RequestParam String url) {
        Integer count = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM sys_banner WHERE image_url = ?) + "
                + "(SELECT COUNT(*) FROM sys_website_config WHERE LOCATE(?, CAST(config_value AS CHAR)) > 0)",
                Integer.class, url, url);
        return Result.success(count != null && count > 0);
    }
}
