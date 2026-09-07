package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.hongtu.DAO.SysRoleMapper;
import org.hongtu.DTO.SysRole;
import org.hongtu.Result.Result;
import org.hongtu.service.SysRoleService;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantScope;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 系统角色表 服务实现类
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    @Override
    public Result<List<SysRole>> getAllRoles() {
        // 查询所有角色，按创建时间降序排序
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(SysRole::getCreateTime);

        List<SysRole> roles = list(queryWrapper);
        return Result.success(roles);
    }


    @Override
    public Result addRole(SysRole role) {
        boolean superAdmin = StpUtil.hasRole("SUPER_ADMIN");
        Long currentTenantId = TenantContext.getTenantIdOrNull();
        if (!superAdmin || currentTenantId != null) {
            role.setTenantId(currentTenantId);
        }
        if ("SUPER_ADMIN".equals(role.getRoleCode()) && role.getTenantId() != null) {
            return Result.error("租户内不能创建平台超级管理员角色");
        }
        // 检查角色编码是否已存在
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(role.getTenantId() != null, SysRole::getTenantId, role.getTenantId())
                .isNull(role.getTenantId() == null, SysRole::getTenantId)
                .eq(SysRole::getRoleCode, role.getRoleCode());
        if (count(queryWrapper) > 0) {
            return Result.error("角色编码已存在");
        }
        // 保存角色
        boolean success = save(role);
        if (success) {
            return Result.success("添加角色成功", role);
        } else {
            return Result.error("添加角色失败");
        }
    }

    @Override
    public Result updateRole(SysRole role) {
        SysRole existingRole = getById(role.getId());
        if (existingRole == null) {
            return Result.error("角色不存在");
        }
        if (TenantContext.isSuperAdmin() && TenantContext.getTenantIdOrNull() == null
                && existingRole.getTenantId() != null) {
            return TenantScope.call(existingRole.getTenantId(), () -> updateRoleInCurrentTenant(role));
        }
        return updateRoleInCurrentTenant(role);
    }

    private Result updateRoleInCurrentTenant(SysRole role) {
        SysRole existingRole = getById(role.getId());
        if (existingRole == null) {
            return Result.error("角色不存在或不在当前租户");
        }
        role.setTenantId(existingRole.getTenantId());
        if (role.getRoleCode() != null && !existingRole.getRoleCode().equals(role.getRoleCode()))
            return Result.error("角色编码创建后不可修改，请新建角色并重新分配");
        role.setRoleCode(existingRole.getRoleCode());
        // 如果修改了角色编码，检查是否与其他角色冲突
        if (!existingRole.getRoleCode().equals(role.getRoleCode())) {
            LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SysRole::getRoleCode, role.getRoleCode())
                    .ne(SysRole::getId, role.getId());
            if (count(queryWrapper) > 0) {
                return Result.error("角色编码已被使用");
            }
        }
        // 保留创建时间，更新修改时间
        role.setUpdateTime(LocalDateTime.now());
        if (List.of("ADMIN", "SUPER_ADMIN", "USER").contains(existingRole.getRoleCode())
                && role.getStatus() != existingRole.getStatus()) return Result.error("基础角色不能停用");
        // 更新角色
        boolean success = updateById(role);
        if (success) {
            return Result.success("更新角色成功");
        } else {
            return Result.error("更新角色失败");
        }
    }

    @Override
    public Result deleteRole(Long id) {
        if (id == null) {
            return Result.error("角色ID不能为空");
        }

        // 检查角色是否存在
        SysRole role = getById(id);
        if (role == null) {
            return Result.error("角色不存在");
        }

        // 检查是否为系统内置角色（如超级管理员、管理员）
        if ("SUPER_ADMIN".equals(role.getRoleCode()) || "ADMIN".equals(role.getRoleCode())|| "USER".equals(role.getRoleCode())) {
            return Result.error("系统内置角色不能删除");
        }

        // 删除角色
        boolean success = removeById(id);
        if (success) {
            return Result.success("删除角色成功");
        } else {
            return Result.error("删除角色失败");
        }
    }
}
