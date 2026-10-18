package org.hongtu.restaurant.controller;

import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 内部检查门店、菜品与打印模板是否引用资源。 */
@RestController
@RequiredArgsConstructor
public class ResourceReferenceController {
    private final JdbcTemplate jdbc;

    /** 文件删除和转私有前查询全部商户的公开资源引用。 */
    @GetMapping("/inner/resource-reference")
    public Result<Boolean> used(@RequestParam String url) {
        String pattern = "%" + url.replace("\\", "\\\\").replace("%", "\\%")
                .replace("_", "\\_") + "%";
        Integer count = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM store_info WHERE store_images LIKE ?) + "
                + "(SELECT COUNT(*) FROM dish WHERE dish_pics LIKE ? AND deleted = 0) + "
                + "(SELECT COUNT(*) FROM dish_category WHERE deleted = 0 AND "
                + "(category_icon = ? OR category_pic = ?)) + "
                + "(SELECT COUNT(*) FROM print_template WHERE logo_url = ?) + "
                + "(SELECT COUNT(*) FROM dish_spec_option WHERE spec_values LIKE ?)",
                Integer.class, pattern, pattern, url, url, url, pattern);
        return Result.success(count != null && count > 0);
    }

}
