package org.hongtu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.DAO.UserLevelMapper;
import org.hongtu.DAO.UserPointsRecordMapper;
import org.hongtu.DTO.SysUser;
import org.hongtu.DTO.UserLevel;
import org.hongtu.DTO.UserPointsRecord;
import org.hongtu.api.dto.MemberRewardSyncDTO;
import org.hongtu.enums.PointsOperationEnum;
import org.hongtu.enums.PointsSourceEnum;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 按餐饮订单最新实付金额幂等同步经验、积分及退款撤回。 */
@Service
public class MemberOrderRewardService {
    private final JdbcTemplate jdbc;
    private final SysUserMapper users;
    private final UserLevelMapper levels;
    private final UserPointsRecordMapper pointsRecords;
    private final UserExpRecordService expRecords;

    public MemberOrderRewardService(JdbcTemplate jdbc, SysUserMapper users, UserLevelMapper levels,
                                    UserPointsRecordMapper pointsRecords, UserExpRecordService expRecords) {
        this.jdbc = jdbc;
        this.users = users;
        this.levels = levels;
        this.pointsRecords = pointsRecords;
        this.expRecords = expRecords;
    }

    /** 同一订单串行入账；配置和会员等级在首次发放时固化，之后退款按该快照重算。 */
    @Transactional(rollbackFor = Exception.class)
    public void sync(MemberRewardSyncDTO request) {
        if (request == null || request.orderNo() == null || request.orderNo().isBlank()
                || request.orderNo().length() > 80 || request.userId() == null
                || request.remainingAmount() == null || request.remainingAmount().signum() < 0
                || request.remainingAmount().scale() > 2)
            throw new BusinessException(400, "订单奖励同步参数无效");
        Long tenantId = TenantContext.getTenantId();
        // 用户行锁同时串行化该会员不同订单的经验和积分变动。
        SysUser user = users.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, request.userId()).last("FOR UPDATE"));
        if (user == null) throw new BusinessException(404, "会员不存在或不属于当前租户");
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT * FROM member_order_reward WHERE tenant_id=? AND order_no=? FOR UPDATE",
                tenantId, request.orderNo());
        if (!existing.isEmpty() && !request.userId().equals(((Number) existing.getFirst().get("user_id")).longValue()))
            throw new BusinessException(409, "订单归属会员不一致");
        if (existing.isEmpty() && request.remainingAmount().signum() == 0) return;
        Map<String, Object> snapshot = existing.isEmpty() ? createSnapshot(tenantId, user, request) : existing.getFirst();
        if (snapshot == null) return; // 未配置规则的方案不自动发放。
        BigDecimal expRate = (BigDecimal) snapshot.get("exp_rate");
        BigDecimal pointsRate = (BigDecimal) snapshot.get("points_rate");
        BigDecimal expMultiplier = (BigDecimal) snapshot.get("exp_multiplier");
        BigDecimal pointsMultiplier = (BigDecimal) snapshot.get("points_multiplier");
        int targetExp = calculate(request.remainingAmount(), expRate, expMultiplier);
        int targetPoints = calculate(request.remainingAmount(), pointsRate, pointsMultiplier);
        int previousExp = ((Number) snapshot.get("awarded_exp")).intValue();
        int previousPoints = ((Number) snapshot.get("awarded_points")).intValue();
        int expDelta = targetExp - previousExp;
        int pointsDelta = targetPoints - previousPoints;
        if (expDelta != 0 || pointsDelta != 0) {
            int currentExp = user.getExp() == null ? 0 : user.getExp();
            int currentPoints = user.getPoints() == null ? 0 : user.getPoints();
            int newExp = Math.max(0, Math.addExact(currentExp, expDelta));
            int newPoints = Math.addExact(currentPoints, pointsDelta);
            UserLevel level = levels.getLevelByExp(newExp);
            if (level == null) throw new BusinessException(409, "当前租户尚未配置有效的会员等级方案");
            user.setExp(newExp).setPoints(newPoints).setLevel(level.getLevelCode()).setUpdateTime(LocalDateTime.now());
            if (users.updateById(user) != 1) throw new IllegalStateException("更新会员奖励失败");
            if (newExp != currentExp)
                expRecords.record(user.getId(), currentExp, newExp,
                        expDelta > 0 ? "ORDER_REWARD" : "ORDER_REFUND", request.orderNo(),
                        expDelta > 0 ? "餐饮订单完成奖励" : "餐饮订单退款撤回");
            if (pointsDelta != 0) recordPoints(user, currentPoints, newPoints, pointsDelta, request.orderNo());
        }
        jdbc.update("UPDATE member_order_reward SET remaining_amount=?,awarded_exp=?,awarded_points=?,updated_at=NOW() "
                        + "WHERE tenant_id=? AND order_no=?",
                request.remainingAmount(), targetExp, targetPoints, tenantId, request.orderNo());
    }

    private Map<String, Object> createSnapshot(Long tenantId, SysUser user, MemberRewardSyncDTO request) {
        List<Map<String, Object>> rules = jdbc.queryForList("SELECT r.scheme_id,r.exp_per_yuan,r.points_per_yuan "
                        + "FROM user_level_scheme s JOIN member_reward_rule r ON r.scheme_id=s.id "
                        + "WHERE s.tenant_id=? AND s.is_active=1 AND s.status=1 LIMIT 1",
                tenantId);
        if (rules.isEmpty()) return null;
        Map<String, Object> rule = rules.getFirst();
        Long schemeId = ((Number) rule.get("scheme_id")).longValue();
        int levelCode = user.getLevel() == null ? 0 : user.getLevel();
        List<Map<String, Object>> multipliers = jdbc.queryForList(
                "SELECT exp_multiplier,points_multiplier FROM member_reward_level_multiplier "
                        + "WHERE scheme_id=? AND level_code=?", schemeId, levelCode);
        BigDecimal expMultiplier = multipliers.isEmpty() ? BigDecimal.ONE
                : (BigDecimal) multipliers.getFirst().get("exp_multiplier");
        BigDecimal pointsMultiplier = multipliers.isEmpty() ? BigDecimal.ONE
                : (BigDecimal) multipliers.getFirst().get("points_multiplier");
        jdbc.update("INSERT INTO member_order_reward(tenant_id,order_no,user_id,scheme_id,level_code,"
                        + "exp_rate,points_rate,exp_multiplier,points_multiplier,remaining_amount,awarded_exp,awarded_points,created_at,updated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,0,0,0,NOW(),NOW())",
                tenantId, request.orderNo(), request.userId(), schemeId, levelCode,
                rule.get("exp_per_yuan"), rule.get("points_per_yuan"), expMultiplier, pointsMultiplier);
        return jdbc.queryForMap("SELECT * FROM member_order_reward WHERE tenant_id=? AND order_no=? FOR UPDATE",
                tenantId, request.orderNo());
    }

    private int calculate(BigDecimal amount, BigDecimal rate, BigDecimal multiplier) {
        try {
            return amount.multiply(rate).multiply(multiplier).setScale(0, RoundingMode.DOWN).intValueExact();
        } catch (ArithmeticException exception) {
            throw new BusinessException(400, "订单奖励超出整数范围，请调整规则");
        }
    }

    private void recordPoints(SysUser user, int before, int after, int delta, String orderNo) {
        UserPointsRecord record = new UserPointsRecord();
        record.setUserId(user.getId()).setUsername(user.getUsername());
        record.setOperationType(delta > 0 ? PointsOperationEnum.INCREASE : PointsOperationEnum.DECREASE);
        record.setSource(PointsSourceEnum.SHOPPING).setPoints(Math.abs(delta));
        record.setBeforePoints(before).setAfterPoints(after);
        record.setRemark((delta > 0 ? "餐饮订单完成奖励：" : "餐饮订单退款撤回：") + orderNo);
        record.setOperationTime(LocalDateTime.now());
        if (pointsRecords.insert(record) != 1) throw new IllegalStateException("记录积分流水失败");
    }
}
