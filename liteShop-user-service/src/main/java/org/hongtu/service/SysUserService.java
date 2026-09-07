package org.hongtu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.SysUser;
import org.hongtu.Result.Result;
import org.hongtu.enums.PointsSourceEnum;

import java.util.List;


/**
 * <p>
 * 系统用户表 服务类
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
public interface SysUserService extends IService<SysUser> {

    Result login(String account, String password);

    Result wxGetPhoneNumber(String code);

    /**
     * 根据角色编码列表获取角色ID列表
     * */
    List<Long> getUserRoleIds(List<String> roleCodes);

    Result wxLoginAndBindPhone(String code);

    Result getUserBasicInfo();

    Result updateUserInfo(SysUser sysUser);

    /**
     * 分页查询用户列表
     * @param currentPage 页码
     * @param pageSize 每页数量
     * @param keyWord 关键词（用户名、昵称、手机号）
     * @param level 用户等级
     * @return 用户列表
     */
    Result listUsers(Integer currentPage , Integer pageSize, String keyWord,Integer level);

    /**
     * 添加用户
     * @param sysUser 用户信息
     * @return 操作结果
     */
    Result addUser(SysUser sysUser);

    /**
     * 删除用户
     * @param id 用户ID
     * @return 操作结果
     */
    Result deleteUser(Long id);

    /**
     * 增加用户积分
     * @param userId 用户ID
     * @param points 积分数量
     * @return 操作结果
     */
    Result addUserPoints(Long userId, Integer points, PointsSourceEnum sourceType,String remark);
    Result addUserPoints(Long userId, Integer points, PointsSourceEnum sourceType);
    Result addUserPoints(Long userId, Integer points,String remark);
    Result addUserPoints(Long userId, Integer points);

    Result phoneOrEmailVerifyCodeLogin(String account,String verifyCode);

    /**
     * 获取用户优惠券数量
     * @param userId 用户ID
     * @return 优惠券数量
     */
    Long getUserCouponCount(Long userId);

    Result adminEditUser(SysUser sysUser);

    Result rechargeBalance(Long userId, Integer amount);

    /**
     * 重置密码（支持手机号和邮箱）
     * @param account 账号（手机号或邮箱）
     * @param verifyCode 验证码
     * @param newPassword 新密码
     * @return 操作结果
     */
    Result resetPassword(String account, String verifyCode, String newPassword);

    Result adminResetPassword(Long userId, String newPassword);

    Result adminPhoneOrEmailVerifyCodeLogin(String account, String verifyCode);

    Result adminLogin(String account, String password);

    Result selectAdminTenant(String selectionTicket, Long tenantId, Long storeId);

    default Result selectAdminTenant(String selectionTicket, Long tenantId) {
        return selectAdminTenant(selectionTicket, tenantId, null);
    }

    Result listAdminWorkspaces();

    Result switchAdminWorkspace(Long tenantId, Long storeId);
}
