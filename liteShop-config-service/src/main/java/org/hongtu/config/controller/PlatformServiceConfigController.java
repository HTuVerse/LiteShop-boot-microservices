package org.hongtu.config.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.PlatformServiceConfigDTO;
import org.hongtu.api.dto.PlatformServiceRuntimeDTO;
import org.hongtu.config.service.PlatformServiceConfigService;
import org.springframework.web.bind.annotation.*;

/** 超级管理员维护平台消息与文件配置，运行参数只通过内部接口提供。 */
@RestController
@RequiredArgsConstructor
public class PlatformServiceConfigController {
    private final PlatformServiceConfigService service;

    /** 读取不含明文密钥的管理表单。 */
    @SaCheckRole("SUPER_ADMIN")
    @GetMapping("/config/platform-message/admin")
    public Result<PlatformServiceConfigDTO> messageConfig() { return Result.success(service.getMessageForEdit()); }

    @SaCheckRole("SUPER_ADMIN")
    @GetMapping("/config/platform-file/admin")
    public Result<PlatformServiceConfigDTO> fileConfig() { return Result.success(service.getFileForEdit()); }

    /** 发布配置后，新请求立即从配置服务读取。 */
    @SaCheckRole("SUPER_ADMIN")
    @PutMapping("/config/platform-message/admin")
    public Result<PlatformServiceConfigDTO> saveMessage(@RequestBody PlatformServiceConfigDTO submitted) {
        return Result.success(service.saveMessage(submitted, StpUtil.getLoginIdAsLong()));
    }

    @SaCheckRole("SUPER_ADMIN")
    @PutMapping("/config/platform-file/admin")
    public Result<PlatformServiceConfigDTO> saveFile(@RequestBody PlatformServiceConfigDTO submitted) {
        return Result.success(service.saveFile(submitted, StpUtil.getLoginIdAsLong()));
    }

    /** 内部消息服务读取平台通道配置。 */
    @GetMapping("/inner/config/platform-message")
    public Result<PlatformServiceRuntimeDTO> message() { return Result.success(service.messageRuntime()); }

    /** 内部文件服务读取平台存储配置。 */
    @GetMapping("/inner/config/platform-file")
    public Result<PlatformServiceRuntimeDTO> file() { return Result.success(service.fileRuntime()); }

    /** 文件服务读取历史资源绑定的存储配置快照。 */
    @GetMapping("/inner/config/platform-file-profile")
    public Result<PlatformServiceRuntimeDTO> fileProfile(@RequestParam("profileId") Long profileId) {
        return Result.success(service.fileRuntime(profileId));
    }

    /** 内部文件读取只获取上传目录。 */
    @GetMapping("/inner/config/platform-file-path")
    public Result<String> filePath() { return Result.success("获取平台上传目录成功", service.currentUploadPath()); }
}
