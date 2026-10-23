package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DAO.UserPointsRecordMapper;
import org.hongtu.DTO.SysUser;
import org.hongtu.DTO.UserPointsRecord;
import org.hongtu.api.dto.MallPointsChangeDTO;
import org.hongtu.api.error.MallErrorCodes;
import org.hongtu.enums.PointsOperationEnum;
import org.hongtu.enums.PointsSourceEnum;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 积分商城账务入口；同一订单的扣减和退回各只能入账一次。 */
@Service
public class MallPointsAccountService {
    @Resource private SysUserMapper userMapper;
    @Resource private UserPointsRecordMapper recordMapper;

    /** 返回变更后的积分余额。 */
    @Transactional(rollbackFor = Exception.class)
    public Integer change(MallPointsChangeDTO dto) {
        if (dto == null || dto.getUserId() == null || dto.getOrderId() == null
                || dto.getPoints() == null || dto.getPoints() <= 0
                || !("SPEND".equals(dto.getAction()) || "REFUND".equals(dto.getAction()))) {
            throw new BusinessException("积分变动参数无效");
        }
        Long tenantId = TenantContext.getTenantId();
        String spendKey = "mall:spend:" + dto.getOrderId();
        String key = "SPEND".equals(dto.getAction()) ? spendKey : "mall:refund:" + dto.getOrderId();
        UserPointsRecord existing = recordMapper.selectOne(new LambdaQueryWrapper<UserPointsRecord>()
                .eq(UserPointsRecord::getIdempotencyKey, key).last("LIMIT 1"));
        if (existing != null) {
            if (!dto.getUserId().equals(existing.getUserId()) || !dto.getPoints().equals(existing.getPoints()))
                throw new BusinessException("兑换订单积分参数不一致");
            return existing.getAfterPoints();
        }
        if ("REFUND".equals(dto.getAction())) {
            UserPointsRecord spend = recordMapper.selectOne(new LambdaQueryWrapper<UserPointsRecord>()
                    .eq(UserPointsRecord::getIdempotencyKey, spendKey).last("LIMIT 1"));
            if (spend == null || !dto.getUserId().equals(spend.getUserId()) || !dto.getPoints().equals(spend.getPoints()))
                throw new BusinessException("兑换扣减记录不存在，不能退回积分");
        }
        SysUser before = userMapper.lockMallPointsUser(tenantId, dto.getUserId());
        if (before == null) throw new BusinessException(MallErrorCodes.POINTS_USER_MISSING, "用户不存在");
        int changed = "SPEND".equals(dto.getAction())
                ? userMapper.deductMallPoints(tenantId, dto.getUserId(), dto.getPoints())
                : userMapper.refundMallPoints(tenantId, dto.getUserId(), dto.getPoints());
        if (changed != 1) throw new BusinessException(MallErrorCodes.POINTS_INSUFFICIENT, "积分不足或用户不存在");
        SysUser after = userMapper.selectById(dto.getUserId());
        UserPointsRecord record = new UserPointsRecord();
        record.setUserId(dto.getUserId()).setUsername(after.getUsername())
                .setOperationType("SPEND".equals(dto.getAction()) ? PointsOperationEnum.DECREASE : PointsOperationEnum.INCREASE)
                .setSource(PointsSourceEnum.EXCHANGE).setPoints(dto.getPoints())
                .setBeforePoints(before.getPoints()).setAfterPoints(after.getPoints())
                .setRemark("积分商城订单 " + dto.getOrderId() + ("SPEND".equals(dto.getAction()) ? " 兑换" : " 失败退回"))
                .setIdempotencyKey(key).setOperationTime(LocalDateTime.now());
        recordMapper.insert(record);
        return after.getPoints();
    }
}
