package org.hongtu.DAO;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 批量机会发放：凭据与余额变更必须在同一个本地事务中提交。 */
@Mapper
public interface LotteryChanceGrantMapper {
    @Getter
    @AllArgsConstructor
    class ChanceRow {
        private final Long id;
        private final Long userId;
    }

    @InterceptorIgnore(tenantLine = "true")
    @Insert("<script>INSERT IGNORE INTO lottery_chance_grant_receipt " +
            "(tenant_id, request_key, user_id, activity_id, chances) VALUES " +
            "<foreach collection='ids' item='userId' separator=','>" +
            "(#{tenantId}, #{requestKey}, #{userId}, #{activityId}, #{chances})" +
            "</foreach></script>")
    int claim(@Param("tenantId") Long tenantId, @Param("requestKey") String requestKey,
              @Param("activityId") Long activityId, @Param("chances") Integer chances,
              @Param("ids") List<Long> ids);

    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>SELECT COUNT(*) FROM lottery_chance_grant_receipt " +
            "WHERE tenant_id=#{tenantId} AND request_key=#{requestKey} " +
            "AND (activity_id != #{activityId} OR chances != #{chances}) " +
            "AND user_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int countConflicts(@Param("tenantId") Long tenantId, @Param("requestKey") String requestKey,
                       @Param("activityId") Long activityId, @Param("chances") Integer chances,
                       @Param("ids") List<Long> ids);

    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>SELECT user_id FROM lottery_chance_grant_receipt " +
            "WHERE tenant_id=#{tenantId} AND request_key=#{requestKey} AND applied=0 " +
            "AND user_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "FOR UPDATE</script>")
    List<Long> lockPending(@Param("tenantId") Long tenantId, @Param("requestKey") String requestKey,
                           @Param("ids") List<Long> ids);

    @InterceptorIgnore(tenantLine = "true")
    @Insert("<script>INSERT INTO user_activity_chance " +
            "(id, tenant_id, user_id, activity_id, chances, create_time, update_time) VALUES " +
            "<foreach collection='rows' item='row' separator=','>" +
            "(#{row.id}, #{tenantId}, #{row.userId}, #{activityId}, #{chances}, NOW(), NOW())" +
            "</foreach> ON DUPLICATE KEY UPDATE chances=chances+VALUES(chances), update_time=NOW()</script>")
    int apply(@Param("tenantId") Long tenantId, @Param("activityId") Long activityId,
              @Param("chances") Integer chances, @Param("rows") List<ChanceRow> rows);

    @InterceptorIgnore(tenantLine = "true")
    @Update("<script>UPDATE lottery_chance_grant_receipt SET applied=1,updated_at=NOW() " +
            "WHERE tenant_id=#{tenantId} AND request_key=#{requestKey} AND applied=0 " +
            "AND user_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int markApplied(@Param("tenantId") Long tenantId, @Param("requestKey") String requestKey,
                    @Param("ids") List<Long> ids);
}
