package org.hongtu.config;

import cn.dev33.satoken.stp.StpInterface;
import jakarta.annotation.Resource;
import org.hongtu.DAO.SysPermissionMapper;
import org.hongtu.DAO.SysRoleMapper;
import org.hongtu.tenant.TenantConstants;
import org.hongtu.tenant.TenantIgnore;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义权限加载接口实现类
 */
@Component    // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {

    @Resource
    SysPermissionMapper sysPermissionMapper;
    @Resource
    SysRoleMapper sysRoleMapper;
    /**
     * 返回一个账号所拥有的权限码集合
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 将登录ID转换为Long类型
        Long userId = Long.valueOf(loginId.toString());
        // 调用Mapper查询用户权限
        List<String> permissionList = isPlatformSuperAdminSession()
                ? TenantIgnore.get(() -> sysPermissionMapper.selectPermissionCodesByUserId(userId))
                : sysPermissionMapper.selectPermissionCodesByUserId(userId);

        // 如果权限列表为空，则返回一个新的空列表
        if (permissionList == null) {
            permissionList = new ArrayList<>();
        }

        return permissionList;
    }


    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 将登录ID转换为Long类型
        Long userId = Long.valueOf(loginId.toString());

        // 调用Mapper查询用户角色
        List<String> roleList = isPlatformSuperAdminSession()
                ? TenantIgnore.get(() -> sysRoleMapper.selectRoleCodesByUserId(userId))
                : sysRoleMapper.selectRoleCodesByUserId(userId);

        // 如果角色列表为空，则返回一个新的空列表
        if (roleList == null) {
            roleList = new ArrayList<>();
        }

        return roleList;
    }

    private boolean isPlatformSuperAdminSession() {
        try {
            return Boolean.parseBoolean(String.valueOf(
                    StpUtil.getSession().get(TenantConstants.SESSION_SUPER_ADMIN)));
        } catch (Exception ignored) {
            return false;
        }
    }

}
