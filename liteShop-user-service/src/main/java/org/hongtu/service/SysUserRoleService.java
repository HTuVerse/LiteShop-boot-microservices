package org.hongtu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.SysUserRole;
import org.hongtu.Result.Result;

import java.util.List;


/**
 * <p>
 * 用户角色关联表 服务类
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
public interface SysUserRoleService extends IService<SysUserRole> {

    Result assignRoles(Long userId, List<Long> roleIds);


}
