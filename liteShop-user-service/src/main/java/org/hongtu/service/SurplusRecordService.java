package org.hongtu.service;

import org.hongtu.DTO.UserSurplusRecord;
import org.hongtu.Result.Result;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 * 余额变动历史表 服务类
 * </p>
 *
 * @author Ht
 * @since 2025-08-25
 */
public interface SurplusRecordService extends IService<UserSurplusRecord> {

    /**
     * 分页查询当前用户的全部余额变动明细
     * @param userId 用户ID（当前登录用户）
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @param type 变动类型（可选，1-用户充值 2-管理员充值 3-退款 4-消费）
     * @return 分页结果
     */
    Result getMyRecordPage(Long userId, Integer pageNum, Integer pageSize, Integer type);
}
