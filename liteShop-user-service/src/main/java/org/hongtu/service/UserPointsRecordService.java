package org.hongtu.service;

import org.hongtu.DTO.UserPointsRecord;
import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.Result.Result;
import org.hongtu.enums.PointsSourceEnum;

/**
 * <p>
 * 用户积分记录DTO 服务类
 * </p>
 *
 * @author Ht
 * @since 2025-08-06
 */
public interface UserPointsRecordService extends IService<UserPointsRecord> {

    /**
     * 分页查询用户积分记录
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 积分记录列表
     */
    Result getUserPointsRecords(Long userId, Integer pageNum, Integer pageSize,PointsSourceEnum source);

    /**
     * 获取用户积分统计信息
     * @param userId 用户ID
     * @return 积分统计信息
     */
    Result getUserPointsStatistics(Long userId);
}
