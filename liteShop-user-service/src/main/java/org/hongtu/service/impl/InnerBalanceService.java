package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.SurplusRecordMapper;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DTO.SysUser;
import org.hongtu.DTO.UserSurplusRecord;
import org.hongtu.api.dto.BalanceChangeDTO;
import org.hongtu.wallet.dto.WalletCreditDTO;
import org.hongtu.tenant.TenantContext;
import org.hongtu.enums.BalanceResourceEnum;
import org.hongtu.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    /** 锁定会员后按商户订单号入账，余额和赠送子余额保持一致。 */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal creditWallet(WalletCreditDTO dto) {
        if (dto == null || dto.getUserId() == null || dto.getAmount() == null || dto.getBonus() == null
                || dto.getAmount().signum() <= 0 || dto.getAmount().scale() > 2
                || dto.getBonus().signum() < 0 || dto.getBonus().scale() > 2
                || dto.getOrderNo() == null || !dto.getOrderNo().matches("W[0-9]+")) {
            throw new BusinessException("储值入账参数无效");
        }
        Long tenantId = TenantContext.getTenantId();
        SysUser locked = sysUserMapper.lockMallPointsUser(tenantId, dto.getUserId());
        if (locked == null) throw new BusinessException("会员不存在或不属于当前租户");
        BigDecimal total = dto.getAmount().add(dto.getBonus());
        UserSurplusRecord existing = surplusRecordMapper.selectOne(new LambdaQueryWrapper<UserSurplusRecord>()
                .eq(UserSurplusRecord::getOrderNo, dto.getOrderNo()).last("LIMIT 1"));
        if (existing != null) {
            if (!dto.getUserId().equals(existing.getUserId()) || existing.getType() != BalanceResourceEnum.USER_RECHARGE
                    || total.compareTo(existing.getAmount()) != 0
                    || dto.getBonus().compareTo(existing.getGiftAmount() == null ? BigDecimal.ZERO : existing.getGiftAmount()) != 0)
                throw new BusinessException("储值订单号已被占用");
            return existing.getNewBalance();
        }
        if (sysUserMapper.creditWallet(tenantId, dto.getUserId(), dto.getAmount(), dto.getBonus()) != 1)
            throw new BusinessException("储值入账失败");
        BigDecimal oldBalance = locked.getSurplusMoney() == null ? BigDecimal.ZERO : locked.getSurplusMoney();
        UserSurplusRecord record = new UserSurplusRecord();
        record.setUserId(dto.getUserId());
        record.setOrderNo(dto.getOrderNo());
        record.setAmount(total);
        record.setGiftAmount(dto.getBonus());
        record.setOldBalance(oldBalance);
        record.setNewBalance(oldBalance.add(total));
        record.setType(BalanceResourceEnum.USER_RECHARGE);
        record.setOperateUserId(dto.getUserId());
        surplusRecordMapper.insert(record);
        return record.getNewBalance();
    }

    /**
     * 余额变动：充值/退款为加，消费为减
     *
     * @return 变动后余额
     */
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal changeBalance(BalanceChangeDTO dto) {
        if (dto == null || dto.getUserId() == null || dto.getAmount() == null
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
        // 同一会员的扣款、退款、充值串行化，分摊比例始终取扣款前余额。
        SysUser locked = sysUserMapper.lockMallPointsUser(TenantContext.getTenantId(), dto.getUserId());
        if (locked == null) throw new BusinessException("会员不存在或不属于当前租户");
        UserSurplusRecord existingRecord = surplusRecordMapper.selectOne(
                new LambdaQueryWrapper<UserSurplusRecord>()
                        .eq(UserSurplusRecord::getOrderNo, dto.getOrderNo())
                        .last("LIMIT 1"));
        if (existingRecord != null) {
            if (!dto.getUserId().equals(existingRecord.getUserId())
                    || type != existingRecord.getType()
                    || dto.getAmount().compareTo(existingRecord.getAmount()) != 0
                    || !java.util.Objects.equals(dto.getSourceOrderNo(), existingRecord.getSourceOrderNo())) {
                throw new BusinessException("余额幂等键已被其它业务占用");
            }
            return existingRecord.getNewBalance();
        }

        BigDecimal giftAmount = BigDecimal.ZERO;
        int updated;
        if (type == BalanceResourceEnum.EXPENSE) {
            updated = sysUserMapper.deductBalance(dto.getUserId(), dto.getAmount());
        } else if (type == BalanceResourceEnum.REFUND && dto.getSourceOrderNo() != null) {
            UserSurplusRecord payment = surplusRecordMapper.selectOne(new LambdaQueryWrapper<UserSurplusRecord>()
                    .eq(UserSurplusRecord::getOrderNo, dto.getSourceOrderNo()).last("LIMIT 1"));
            if (payment == null || !dto.getUserId().equals(payment.getUserId())
                    || payment.getType() != BalanceResourceEnum.EXPENSE)
                throw new BusinessException("原余额支付流水不存在");
            var refunds = surplusRecordMapper.selectList(new LambdaQueryWrapper<UserSurplusRecord>()
                    .eq(UserSurplusRecord::getSourceOrderNo, dto.getSourceOrderNo())
                    .eq(UserSurplusRecord::getType, BalanceResourceEnum.REFUND));
            BigDecimal refunded = refunds.stream().map(UserSurplusRecord::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal refundedGift = refunds.stream().map(row -> row.getGiftAmount() == null ? BigDecimal.ZERO : row.getGiftAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remaining = payment.getAmount().subtract(refunded);
            if (dto.getAmount().compareTo(remaining) > 0) throw new BusinessException("余额退款超过原支付金额");
            BigDecimal originalGift = payment.getGiftAmount() == null ? BigDecimal.ZERO : payment.getGiftAmount();
            BigDecimal remainingGift = originalGift.subtract(refundedGift);
            giftAmount = dto.getAmount().compareTo(remaining) == 0 ? remainingGift
                    : dto.getAmount().multiply(originalGift).divide(payment.getAmount(), 2, RoundingMode.HALF_UP)
                        .min(remainingGift);
            updated = sysUserMapper.refundBalance(TenantContext.getTenantId(), dto.getUserId(), dto.getAmount(), giftAmount);
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
        BigDecimal oldBalance = locked.getSurplusMoney() == null ? BigDecimal.ZERO : locked.getSurplusMoney();
        if (type == BalanceResourceEnum.EXPENSE) {
            BigDecimal oldGift = locked.getGiftMoney() == null ? BigDecimal.ZERO : locked.getGiftMoney();
            BigDecimal newGift = user.getGiftMoney() == null ? BigDecimal.ZERO : user.getGiftMoney();
            giftAmount = oldGift.subtract(newGift);
        }

        UserSurplusRecord record = new UserSurplusRecord();
        record.setUserId(dto.getUserId());
        record.setOrderNo(dto.getOrderNo());
        record.setAmount(dto.getAmount());
        record.setGiftAmount(giftAmount);
        record.setSourceOrderNo(dto.getSourceOrderNo());
        record.setOldBalance(oldBalance);
        record.setNewBalance(newBalance);
        record.setType(type);
        record.setOperateUserId(dto.getOperateUserId() == null ? dto.getUserId() : dto.getOperateUserId());
        surplusRecordMapper.insert(record);

        log.info("余额变动成功 userId={}, type={}, amount={}, oldBalance={}, newBalance={}, orderNo={}",
                dto.getUserId(), type, dto.getAmount(), oldBalance, newBalance, dto.getOrderNo());
        return newBalance;
    }

    /** 精确撤销一次退款入账，并以撤销流水号保证重试不重复扣款。 */
    @Transactional(rollbackFor = Exception.class)
    public boolean reverseCredit(String orderNo, Long userId, String reversalNo) {
        if (orderNo == null || orderNo.isBlank() || userId == null
                || reversalNo == null || reversalNo.isBlank() || reversalNo.length() > 64) {
            throw new BusinessException("退款撤销参数无效");
        }
        Long tenantId = TenantContext.getTenantId();
        SysUser locked = sysUserMapper.lockMallPointsUser(tenantId, userId);
        if (locked == null) throw new BusinessException("会员不存在或不属于当前租户");
        UserSurplusRecord existing = surplusRecordMapper.selectOne(new LambdaQueryWrapper<UserSurplusRecord>()
                .eq(UserSurplusRecord::getOrderNo, reversalNo).last("LIMIT 1"));
        if (existing != null) {
            if (!userId.equals(existing.getUserId()) || existing.getType() != BalanceResourceEnum.EXPENSE
                    || !orderNo.equals(existing.getSourceOrderNo())) {
                throw new BusinessException("余额撤销幂等键已被占用");
            }
            return true;
        }
        UserSurplusRecord credit = surplusRecordMapper.selectOne(new LambdaQueryWrapper<UserSurplusRecord>()
                .eq(UserSurplusRecord::getOrderNo, orderNo).last("LIMIT 1"));
        if (credit == null) return false;
        if (!userId.equals(credit.getUserId()) || credit.getType() != BalanceResourceEnum.REFUND) {
            throw new BusinessException("原退款流水不匹配");
        }
        BigDecimal gift = credit.getGiftAmount() == null ? BigDecimal.ZERO : credit.getGiftAmount();
        if (sysUserMapper.reverseBalanceCredit(tenantId, userId, credit.getAmount(), gift) != 1) {
            throw new BusinessException("撤销退款余额不足，等待对账重试");
        }
        BigDecimal before = locked.getSurplusMoney();
        UserSurplusRecord reversal = new UserSurplusRecord()
                .setUserId(userId).setOrderNo(reversalNo).setSourceOrderNo(orderNo)
                .setAmount(credit.getAmount()).setGiftAmount(gift)
                .setOldBalance(before).setNewBalance(before.subtract(credit.getAmount()))
                .setType(BalanceResourceEnum.EXPENSE)
                .setOperateUserId(userId);
        surplusRecordMapper.insert(reversal);
        return true;
    }
}
