package org.hongtu.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.SysTenant;
import org.hongtu.Result.Result;

public interface SysTenantService extends IService<SysTenant> {

    SysTenant findByCode(String tenantCode);

    Result<Page<SysTenant>> pageTenants(Integer currentPage, Integer pageSize, String keyword, Integer status);

    Result<SysTenant> addTenant(SysTenant tenant);

    Result<SysTenant> updateTenant(SysTenant tenant);

    Result<Void> updateStatus(Long tenantId, Integer status);
}
