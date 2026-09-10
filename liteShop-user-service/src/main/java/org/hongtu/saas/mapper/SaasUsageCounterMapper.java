package org.hongtu.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.hongtu.saas.domain.SaasUsageCounter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface SaasUsageCounterMapper extends BaseMapper<SaasUsageCounter> {
    @Insert("""
        INSERT INTO saas_usage_counter
          (id, tenant_id, feature_code, period_start, period_end, used_value, version, create_time, update_time)
        VALUES
          (#{id}, #{tenantId}, #{featureCode}, #{periodStart}, #{periodEnd}, #{baseline}, 1, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
          version = IF(used_value < #{baseline}, version + 1, version),
          update_time = IF(used_value < #{baseline}, NOW(), update_time),
          used_value = GREATEST(used_value, #{baseline})
        """)
    int ensureAtLeast(@Param("id") Long id, @Param("tenantId") Long tenantId,
                      @Param("featureCode") String featureCode,
                      @Param("periodStart") LocalDateTime periodStart,
                      @Param("periodEnd") LocalDateTime periodEnd,
                      @Param("baseline") BigDecimal baseline);

    @Insert("""
        INSERT INTO saas_usage_counter
          (id, tenant_id, feature_code, period_start, period_end, used_value, version, create_time, update_time)
        VALUES
          (#{id}, #{tenantId}, #{featureCode}, #{periodStart}, #{periodEnd}, #{amount}, 1, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
          version = IF(used_value + #{amount} <= #{limitValue}, version + 1, version),
          update_time = IF(used_value + #{amount} <= #{limitValue}, NOW(), update_time),
          used_value = IF(used_value + #{amount} <= #{limitValue}, used_value + #{amount}, used_value)
        """)
    int tryConsume(@Param("id") Long id, @Param("tenantId") Long tenantId,
                   @Param("featureCode") String featureCode,
                   @Param("periodStart") LocalDateTime periodStart,
                   @Param("periodEnd") LocalDateTime periodEnd,
                   @Param("amount") BigDecimal amount,
                   @Param("limitValue") BigDecimal limitValue);

    @Insert("""
        INSERT INTO saas_usage_counter
          (id, tenant_id, feature_code, period_start, period_end, used_value, version, create_time, update_time)
        VALUES
          (#{id}, #{tenantId}, #{featureCode}, #{periodStart}, #{periodEnd}, #{amount}, 1, NOW(), NOW())
        ON DUPLICATE KEY UPDATE used_value = used_value + #{amount}, version = version + 1, update_time = NOW()
        """)
    int consumeUnlimited(@Param("id") Long id, @Param("tenantId") Long tenantId,
                         @Param("featureCode") String featureCode,
                         @Param("periodStart") LocalDateTime periodStart,
                         @Param("periodEnd") LocalDateTime periodEnd,
                         @Param("amount") BigDecimal amount);

    @Update("""
        UPDATE saas_usage_counter
        SET used_value = GREATEST(used_value - #{amount}, 0), version = version + 1, update_time = NOW()
        WHERE tenant_id = #{tenantId} AND feature_code = #{featureCode} AND period_start = #{periodStart}
        """)
    int release(@Param("tenantId") Long tenantId, @Param("featureCode") String featureCode,
                @Param("periodStart") LocalDateTime periodStart, @Param("amount") BigDecimal amount);
}
