package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hongtu.DTO.UserSurplusRecord;
import org.hongtu.DAO.SurplusRecordMapper;
import org.hongtu.Result.Result;
import org.hongtu.enums.BalanceResourceEnum;
import org.hongtu.service.SurplusRecordService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static org.hongtu.constant.CommonConst.MAX_QUERY_YEARS;

/**
 * <p>
 * 余额变动历史表 服务实现类
 * </p>
 *
 * @author Ht
 * @since 2025-08-25
 */
@Service
public class SurplusRecordServiceImpl extends ServiceImpl<SurplusRecordMapper, UserSurplusRecord> implements SurplusRecordService {

    @Override
    public Result getMyRecordPage(Long userId, Integer pageNum, Integer pageSize, Integer type) {
        if (userId == null || pageNum == null || pageSize == null) {
            return Result.error("必要参数不能为空");
        }
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(MAX_QUERY_YEARS);
        Page<UserSurplusRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<UserSurplusRecord> q = new LambdaQueryWrapper<>();
        q.eq(UserSurplusRecord::getUserId, userId)
                .ge(UserSurplusRecord::getCreatedAt, oneYearAgo)
                .orderByDesc(UserSurplusRecord::getCreatedAt);
        if (type != null) {
            BalanceResourceEnum typeEnum = BalanceResourceEnum.getByCode(type);
            if (typeEnum != null) {
                q.eq(UserSurplusRecord::getType, typeEnum);
            }
        }
        Page<UserSurplusRecord> result = page(page, q);
        return Result.success("查询成功", result);
    }
}
