package org.hongtu.service.impl;

import cn.hutool.core.util.NumberUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.SurplusRecordMapper;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DTO.SysUser;
import org.hongtu.DTO.UserSurplusRecord;
import org.hongtu.api.dto.BalanceChangeDTO;
import org.hongtu.enums.BalanceResourceEnum;
import org.hongtu.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 余额变动内部服务（供 /inner/user/balance/change 使用）
 * <p>
 * 替代原单体中 restaurant 模块直接「改 SysUser.surplusMoney + 插 UserSurplusRecord」的跨模块操作，
 * 迁移后余额的读改写收敛到 user-service 本地事务内，保证原子性。
 */
@Slf4j
@Service
public class InnerBalanceService {

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private SurplusRecordMapper surplusRecordMapper;

    /**
     * 余额变动：充值/退款为加，消费为减
     *
     * @return 变动后余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal changeBalance(BalanceChangeDTO dto) {
        if (dto.getUserId() == null || dto.getAmount() == null
                || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0 || dto.getType() == null) {
            throw new BusinessException("余额变动参数错误");
        }
        BalanceResourceEnum type = BalanceResourceEnum.getByCode(dto.getType());
        if (type == null) {
            throw new BusinessException("无效的余额变动类型: " + dto.getType());
        }

        // orderNo 是跨服务幂等键。远程调用超时后重试时直接返回第一次结果，不能再次加减余额。
        if (dto.getOrderNo() == null || dto.getOrderNo().isBlank()) {
            throw new BusinessException("余额变动必须提供幂等订单号");
        }
        UserSurplusRecord existingRecord = surplusRecordMapper.selectOne(
                new LambdaQueryWrapper<UserSurplusRecord>()
                        .eq(UserSurplusRecord::getOrderNo, dto.getOrderNo())
                        .last("LIMIT 1"));
        if (existingRecord != null) {
            if (!dto.getUserId().equals(existingRecord.getUserId())
                    || type != existingRecord.getType()
                    || dto.getAmount().compareTo(existingRecord.getAmount()) != 0) {
                throw new BusinessException("余额幂等键已被其它业务占用");
            }
            return existingRecord.getNewBalance();
        }

        int updated;
        if (type == BalanceResourceEnum.EXPENSE) {
            updated = sysUserMapper.deductBalance(dto.getUserId(), dto.getAmount());
        } else {
            updated = sysUserMapper.addBalance(dto.getUserId(), dto.getAmount());
        }
        if (updated <= 0) {
            SysUser existing = sysUserMapper.selectById(dto.getUserId());
            if (existing == null) {
                throw new BusinessException("用户不存在");
            }
            if (type == BalanceResourceEnum.EXPENSE) {
                throw new BusinessException("余额不足");
            }
            throw new BusinessException("余额更新失败");
        }

        SysUser user = sysUserMapper.selectById(dto.getUserId());
        BigDecimal newBalance = user.getSurplusMoney() == null ? BigDecimal.ZERO : user.getSurplusMoney();
        BigDecimal oldBalance = type == BalanceResourceEnum.EXPENSE
                ? NumberUtil.add(newBalance, dto.getAmount())
                : NumberUtil.sub(newBalance, dto.getAmount());

        UserSurplusRecord record = new UserSurplusRecord();
        record.setUserId(dto.getUserId());
        record.setOrderNo(dto.getOrderNo());
        record.setAmount(dto.getAmount());
        record.setOldBalance(oldBalance);
        record.setNewBalance(newBalance);
        record.setType(type);
        record.setOperateUserId(dto.getOperateUserId() == null ? dto.getUserId() : dto.getOperateUserId());
        surplusRecordMapper.insert(record);

        log.info("余额变动成功 userId={}, type={}, amount={}, oldBalance={}, newBalance={}, orderNo={}",
                dto.getUserId(), type, dto.getAmount(), oldBalance, newBalance, dto.getOrderNo());
        return newBalance;
    }
}
