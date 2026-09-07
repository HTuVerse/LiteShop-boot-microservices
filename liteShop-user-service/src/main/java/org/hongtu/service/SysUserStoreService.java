package org.hongtu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.SysUserStore;
import org.hongtu.Result.Result;

import java.util.List;

public interface SysUserStoreService extends IService<SysUserStore> {

    List<Long> getStoreIds(Long userId);
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<org.hongtu.VO.UserStoreAuthorizationVO> authorizationPage(int currentPage, int pageSize, String keyword, Long storeId, Boolean authorized);

    Result<Void> assignStores(Long userId, List<Long> storeIds, Long defaultStoreId);
}
