package org.hongtu.saas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.hongtu.saas.domain.SaasAiGeneration;

import java.time.LocalDateTime;
import java.util.List;

/** AI 店长生成记录持久化。 */
@Mapper
public interface SaasAiGenerationMapper extends BaseMapper<SaasAiGeneration> {
    /** 后台回收跨租户查询；结果仅含回收所需ID。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT tenant_id AS tenantId, request_id AS requestId FROM saas_ai_generation "
            + "WHERE status='PROCESSING' AND created_at < #{before} ORDER BY created_at LIMIT 100")
    List<SaasAiGeneration> selectStale(@Param("before") LocalDateTime before);
}
