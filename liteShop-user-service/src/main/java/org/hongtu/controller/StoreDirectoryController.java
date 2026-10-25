package org.hongtu.controller;

import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.StoreDirectoryDTO;
import org.hongtu.service.StoreDirectoryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 内部调用密钥验证后接收餐厅服务的门店目录同步。 */
@RestController
@RequestMapping("/inner/user/store-directory")
@RequiredArgsConstructor
public class StoreDirectoryController {
    private final StoreDirectoryService service;

    /** 在已验证的租户上下文中幂等写入门店目录。 */
    @PostMapping("/sync")
    public Result<Void> sync(@RequestBody List<StoreDirectoryDTO> stores) {
        service.sync(stores);
        return Result.success();
    }
}
