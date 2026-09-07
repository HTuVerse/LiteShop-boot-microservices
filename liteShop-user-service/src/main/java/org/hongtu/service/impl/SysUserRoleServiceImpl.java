package org.hongtu.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.hongtu.DAO.SysRoleMapper;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DAO.SysUserRoleMapper;
import org.hongtu.DTO.SysRole;
import org.hongtu.DTO.SysUser;
import org.hongtu.DTO.SysUserRole;
import org.hongtu.Result.Result;
import org.hongtu.service.SysUserRoleService;
import org.hongtu.tenant.TenantConstants;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户角色关联表 服务实现类
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements SysUserRoleService {

    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysRoleMapper sysRoleMapper;
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result assignRoles(Long userId, List<Long> roleIds) {
        if (userId == null) {
            return Result.error("用户ID不能为空");
        }

        try {
            // 1. 检查用户是否存在
            SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getId, userId).last("FOR UPDATE"));
            if (user == null) {
                return Result.error("用户不存在");
            }

            // 2. 查询该用户现有的角色ID列表
            List<Long> existingRoleIds = baseMapper.selectRoleIdsByUserId(userId);
            boolean platformAdmin = StpUtil.hasRole("SUPER_ADMIN");
            boolean tenantAdmin = StpUtil.hasRole("ADMIN");
            List<String> targetCodes = sysRoleMapper.selectRoleCodesByUserId(userId);
            if (!platformAdmin && (targetCodes.contains("SUPER_ADMIN")
                    || (!tenantAdmin && targetCodes.contains("ADMIN"))))
                return Result.error("无权修改管理员账号的角色");
            roleIds = roleIds == null ? List.of() : roleIds.stream().distinct().toList();
            if (roleIds.stream().anyMatch(Objects::isNull)) return Result.error("角色ID不能为空");

            // 3. 如果新角色列表为空，则清空所有角色
            if (roleIds == null || roleIds.isEmpty()) {
                if (!existingRoleIds.isEmpty()) {
                    LambdaQueryWrapper<SysUserRole> deleteWrapper = new LambdaQueryWrapper<>();
                    deleteWrapper.eq(SysUserRole::getUserId, userId);
                    remove(deleteWrapper);
                }
                refreshStoreRestrictionSession(userId, List.of());
                return Result.success("已清空用户角色");
            }

            // 4. 检查角色是否都存在
            List<SysRole> roles = sysRoleMapper.selectByIds(roleIds);
            if (roles.size() != roleIds.size()) {
                return Result.error("存在无效的角色ID");
            }
            if (roles.stream().anyMatch(role -> !Objects.equals(role.getTenantId(), user.getTenantId()))) {
                return Result.error("不能给用户分配其他租户的角色");
            }

            if (roles.stream().anyMatch(role -> role.getStatus() != org.hongtu.enums.StatusEnum.NORMAL))
                return Result.error("不能分配已停用的角色");
            if (!platformAdmin && roles.stream().anyMatch(role -> "SUPER_ADMIN".equals(role.getRoleCode())
                    || (!tenantAdmin && "ADMIN".equals(role.getRoleCode()))))
                return Result.error("无权分配管理员角色");
            if (!platformAdmin) {
                var ownPermissions = new java.util.HashSet<>(StpUtil.getPermissionList());
                boolean exceedsScope = roles.stream().filter(role -> !existingRoleIds.contains(role.getId()))
                        .flatMap(role -> sysRoleMapper.selectPermissionsByRoleId(role.getId()).stream())
                        .map(org.hongtu.DTO.SysPermission::getPermissionCode)
                        .anyMatch(code -> code != null && !ownPermissions.contains(code));
                if (exceedsScope) return Result.error("不能分配超出自己权限范围的角色");
            }
            // 5. 计算需要删除和添加的角色
            // 需要删除的角色ID：existingRoleIds中有但roleIds中没有的
            List<Long> requestedRoleIds = roleIds;
            List<Long> toDeleteIds = existingRoleIds.stream()
                    .filter(id -> !requestedRoleIds.contains(id))
                    .collect(Collectors.toList());

            // 需要添加的角色ID：roleIds中有但existingRoleIds中没有的
            List<Long> toAddIds = roleIds.stream()
                    .filter(id -> !existingRoleIds.contains(id))
                    .collect(Collectors.toList());

            // 6. 删除需要删除的角色
            if (!toDeleteIds.isEmpty()) {
                LambdaQueryWrapper<SysUserRole> deleteWrapper = new LambdaQueryWrapper<>();
                deleteWrapper.eq(SysUserRole::getUserId, userId)
                              .in(SysUserRole::getRoleId, toDeleteIds);
                remove(deleteWrapper);
            }

            // 7. 添加需要添加的角色
            if (!toAddIds.isEmpty()) {
                List<SysUserRole> userRoles = new ArrayList<>();
                for (Long roleId : toAddIds) {
                    SysUserRole userRole = new SysUserRole();
                    userRole.setTenantId(user.getTenantId());
                    userRole.setUserId(userId);
                    userRole.setRoleId(roleId);
                    userRoles.add(userRole);
                }
                if (!saveBatch(userRoles)) throw new IllegalStateException("保存用户角色失败");
            }

            refreshStoreRestrictionSession(userId, roles);

            return Result.success("角色分配成功");
        } catch (Exception e) {
            throw new org.hongtu.exception.BusinessException(400, "角色分配失败：" + e.getMessage(), e);
        }
    }

    private void refreshStoreRestrictionSession(Long userId, List<SysRole> roles) {
        SaSession loginSession = StpUtil.getSessionByLoginId(userId, false);
        if (loginSession == null) {
            return;
        }
        List<String> roleCodes = roles.stream().filter(role -> role.getStatus() == org.hongtu.enums.StatusEnum.NORMAL).map(SysRole::getRoleCode).toList();
        boolean restricted = !roleCodes.contains(TenantConstants.ROLE_ADMIN)
                && !roleCodes.contains(TenantConstants.ROLE_SUPER_ADMIN)
                && (roleCodes.isEmpty() || roleCodes.stream().anyMatch(code -> !"USER".equals(code)));
        org.hongtu.service.SessionAfterCommit.run(() -> loginSession.set(TenantConstants.SESSION_STORE_RESTRICTED, restricted));
    }

}
