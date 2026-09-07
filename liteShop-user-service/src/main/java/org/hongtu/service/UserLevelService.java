package org.hongtu.service;

import org.hongtu.DTO.UserLevel;
import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.Result.Result;

/**
 * <p>
 * 用户等级配置表 服务类
 * </p>
 *
 * @author Ht
 * @since 2025-08-23
 */
public interface UserLevelService extends IService<UserLevel> {

    Result listLevels();

    Result addLevel(UserLevel userLevel);

    Result updateLevel(UserLevel userLevel);

    Result deleteLevel(Long id);

    Result updateLevelStatus(Long id);

    Result updateUserLevel(Long userId,Integer levelCode);

    /**
     * 增加用户经验值
     * @param userId 用户ID
     * @param exp 增加的经验值
     * @param reason 增加经验值的原因
     * @return 操作结果，包含用户当前等级和经验值信息
     */
    Result increaseUserExp(Long userId, Integer exp, String reason);

    /**
     * 减少用户经验值
     * @param userId 用户ID
     * @param exp 减少的经验值
     * @param reason 减少经验值的原因
     * @return 操作结果，包含用户当前等级和经验值信息
     */
    Result decreaseUserExp(Long userId, Integer exp, String reason);

    Result listEnabledLevels();

}
