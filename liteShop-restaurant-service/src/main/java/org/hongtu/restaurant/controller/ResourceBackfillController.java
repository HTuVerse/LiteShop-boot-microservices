package org.hongtu.restaurant.controller;

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

/** 餐饮库各图片字段的分批历史回填入口。 */
@RestController
@RequiredArgsConstructor
@SaCheckRole("SUPER_ADMIN")
@RequestMapping("/restaurant/admin/resource-reference-backfill")
public class ResourceBackfillController {
    private static final Map<String, ResourceBackfillEngine.Source> SOURCES = Map.of(
            "dish", new ResourceBackfillEngine.Source("dish", "DISH", "AND deleted=0",
                    List.of(new ResourceBackfillEngine.Field("dish_pics", "dishPics", ResourceBackfillEngine.Format.MULTI))),
            "category", new ResourceBackfillEngine.Source("dish_category", "CATEGORY", "AND deleted=0",
                    List.of(new ResourceBackfillEngine.Field("category_pic", "categoryPic", ResourceBackfillEngine.Format.SINGLE),
                            new ResourceBackfillEngine.Field("category_icon", "categoryIcon", ResourceBackfillEngine.Format.SINGLE))),
            "store", new ResourceBackfillEngine.Source("store_info", "STORE", "",
                    List.of(new ResourceBackfillEngine.Field("store_images", "storeImages", ResourceBackfillEngine.Format.MULTI))),
            "template", new ResourceBackfillEngine.Source("print_template", "TEMPLATE", "",
                    List.of(new ResourceBackfillEngine.Field("logo_url", "logoUrl", ResourceBackfillEngine.Format.SINGLE))),
            "spec", new ResourceBackfillEngine.Source("dish_spec_option", "SPEC", "",
                    List.of(new ResourceBackfillEngine.Field("spec_values", "specValues", ResourceBackfillEngine.Format.SPEC_JSON))));

    private final JdbcTemplate jdbc;
    private final ResourceBindingCoordinator references;

    /** 按业务类型和主键游标回填，不修改业务表 URL 或 JSON 内容。 */
    @PostMapping
    public Result<Map<String, Object>> backfill(@RequestParam String type,
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "false") boolean apply) {
        var source = SOURCES.get(type);
        if (source == null) return Result.error("不支持的回填业务类型");
        return Result.success(new ResourceBackfillEngine(jdbc, references).run(source, afterId, limit, apply));
    }
}
