package org.hongtu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hongtu.DAO.UserExpRecordMapper;
import org.hongtu.DTO.UserExpRecord;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 会员经验变动的写入与分页查询。 */
@Service
public class UserExpRecordService {
    private final UserExpRecordMapper records;

    public UserExpRecordService(UserExpRecordMapper records) { this.records = records; }

    /** 与经验余额更新共用调用方事务，只记录实际发生的增减。 */
    public void record(Long userId, int before, int after, String source, String orderNo, String remark) {
        if (before == after) return;
        UserExpRecord row = new UserExpRecord()
                .setTenantId(TenantContext.getTenantId()).setUserId(userId).setChangeExp(after - before)
                .setBeforeExp(before).setAfterExp(after).setSource(source)
                .setOrderNo(orderNo).setRemark(remark).setOperationTime(LocalDateTime.now());
        if (records.insert(row) != 1) throw new IllegalStateException("记录会员经验流水失败");
    }

    /** 仅按当前租户内的会员ID查询，最多每页100条。 */
    public Page<UserExpRecord> page(Long userId, Integer pageNum, Integer pageSize) {
        if (userId == null || pageNum == null || pageNum < 1 || pageSize == null || pageSize < 1 || pageSize > 100)
            throw new BusinessException(400, "分页参数无效");
        return records.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<UserExpRecord>().eq(UserExpRecord::getUserId, userId)
                        .orderByDesc(UserExpRecord::getOperationTime).orderByDesc(UserExpRecord::getId));
    }
}
