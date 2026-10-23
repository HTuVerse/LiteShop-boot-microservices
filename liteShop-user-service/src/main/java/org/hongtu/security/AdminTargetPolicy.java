package org.hongtu.security;

import cn.dev33.satoken.stp.StpUtil;

import java.util.List;

/** 管理端修改用户资料与角色时共用的目标账号层级规则。 */
public final class AdminTargetPolicy {
    private AdminTargetPolicy() {
    }

    /** 返回拒绝原因；普通管理员不能管理同级管理员，超级管理员不能修改超级管理员账号。 */
    public static String denialReason(List<String> targetRoleCodes) {
        if (targetRoleCodes == null) {
            return "无法确认目标用户角色";
        }
        if (targetRoleCodes.contains("SUPER_ADMIN")) {
            return "无法修改超级管理员账号";
        }
        if (targetRoleCodes.contains("ADMIN") && !StpUtil.hasRole("SUPER_ADMIN")) {
            return "无权修改其他管理员账号";
        }
        return null;
    }
}
