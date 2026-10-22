package org.hongtu.service;

import org.hongtu.DTO.UserLevel;
import org.hongtu.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 按会员等级方案保存餐饮订单的经验和积分规则。 */
@Service
public class MemberRewardRuleService {
    private final JdbcTemplate jdbc;

    public MemberRewardRuleService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record LevelMultiplier(Integer levelCode, BigDecimal expMultiplier, BigDecimal pointsMultiplier) {}
    public record RuleConfig(BigDecimal expPerYuan, BigDecimal pointsPerYuan, List<LevelMultiplier> levels) {}

    /** 读取方案规则；新方案默认不发放，所有等级默认一倍。 */
    public RuleConfig get(Long schemeId, List<UserLevel> levels) {
        List<Map<String, Object>> rules = jdbc.queryForList(
                "SELECT exp_per_yuan,points_per_yuan FROM member_reward_rule WHERE scheme_id=?", schemeId);
        BigDecimal exp = rules.isEmpty() ? BigDecimal.ZERO : (BigDecimal) rules.getFirst().get("exp_per_yuan");
        BigDecimal points = rules.isEmpty() ? BigDecimal.ZERO : (BigDecimal) rules.getFirst().get("points_per_yuan");
        Map<Integer, LevelMultiplier> saved = new java.util.HashMap<>();
        jdbc.query("SELECT level_code,exp_multiplier,points_multiplier FROM member_reward_level_multiplier WHERE scheme_id=?",
                rs -> {
                    saved.put(rs.getInt("level_code"), new LevelMultiplier(rs.getInt("level_code"),
                            rs.getBigDecimal("exp_multiplier"), rs.getBigDecimal("points_multiplier")));
                }, schemeId);
        return new RuleConfig(exp, points, levels.stream().map(level -> saved.getOrDefault(level.getLevelCode(),
                new LevelMultiplier(level.getLevelCode(), BigDecimal.ONE, BigDecimal.ONE))).toList());
    }

    /** 保存基础换算值和当前方案所有等级的倍率。 */
    @Transactional(rollbackFor = Exception.class)
    public void save(Long schemeId, Long tenantId, List<UserLevel> levels, RuleConfig config) {
        if (config == null || config.levels() == null) throw new BusinessException(400, "请填写完整的经验积分规则");
        requireDecimal(config.expPerYuan(), 4, new BigDecimal("10000"), "每元经验");
        requireDecimal(config.pointsPerYuan(), 4, new BigDecimal("10000"), "每元积分");
        Set<Integer> expected = new HashSet<>();
        levels.forEach(level -> expected.add(level.getLevelCode()));
        Set<Integer> received = new HashSet<>();
        for (LevelMultiplier row : config.levels()) {
            if (row == null || row.levelCode() == null || !received.add(row.levelCode()))
                throw new BusinessException(400, "等级倍率有重复或缺失");
            requireDecimal(row.expMultiplier(), 4, new BigDecimal("100"), "经验倍率");
            requireDecimal(row.pointsMultiplier(), 4, new BigDecimal("100"), "积分倍率");
        }
        if (!expected.equals(received)) throw new BusinessException(400, "等级已发生变化，请刷新后重新配置");
        jdbc.update("INSERT INTO member_reward_rule(scheme_id,tenant_id,exp_per_yuan,points_per_yuan,created_at,updated_at) "
                        + "VALUES(?,?,?,?,NOW(),NOW()) ON DUPLICATE KEY UPDATE exp_per_yuan=VALUES(exp_per_yuan),"
                        + "points_per_yuan=VALUES(points_per_yuan),updated_at=NOW()",
                schemeId, tenantId, config.expPerYuan(), config.pointsPerYuan());
        jdbc.update("DELETE FROM member_reward_level_multiplier WHERE scheme_id=?", schemeId);
        for (LevelMultiplier row : config.levels())
            jdbc.update("INSERT INTO member_reward_level_multiplier(scheme_id,level_code,tenant_id,exp_multiplier,points_multiplier) VALUES(?,?,?,?,?)",
                    schemeId, row.levelCode(), tenantId, row.expMultiplier(), row.pointsMultiplier());
    }

    /** 复制等级方案时保留其奖励规则，之后各方案独立维护。 */
    public void copy(Long sourceId, Long targetId, Long tenantId) {
        jdbc.update("INSERT INTO member_reward_rule(scheme_id,tenant_id,exp_per_yuan,points_per_yuan,created_at,updated_at) "
                        + "SELECT ?,?,exp_per_yuan,points_per_yuan,NOW(),NOW() FROM member_reward_rule WHERE scheme_id=?",
                targetId, tenantId, sourceId);
        jdbc.update("INSERT INTO member_reward_level_multiplier(scheme_id,level_code,tenant_id,exp_multiplier,points_multiplier) "
                        + "SELECT ?,level_code,?,exp_multiplier,points_multiplier FROM member_reward_level_multiplier WHERE scheme_id=?",
                targetId, tenantId, sourceId);
    }

    public void delete(Long schemeId) {
        jdbc.update("DELETE FROM member_reward_level_multiplier WHERE scheme_id=?", schemeId);
        jdbc.update("DELETE FROM member_reward_rule WHERE scheme_id=?", schemeId);
    }

    private void requireDecimal(BigDecimal value, int scale, BigDecimal max, String label) {
        if (value == null || value.signum() < 0 || value.compareTo(max) > 0 || value.scale() > scale)
            throw new BusinessException(400, label + "必须是0到" + max.toPlainString() + "之间且最多" + scale + "位小数");
    }
}
