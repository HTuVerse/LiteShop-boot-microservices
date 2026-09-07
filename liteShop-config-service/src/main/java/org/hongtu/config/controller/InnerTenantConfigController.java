package org.hongtu.config.controller;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.config.service.TenantConfigDefaults;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/inner/config")
public class InnerTenantConfigController {
    @Resource private TenantConfigDefaults defaults;
    @PostMapping("/initialize") public Result<Boolean> initialize(@RequestParam(required=false) String appName) {
        defaults.ensureDefaults(appName);return Result.success(true);
    }
}
