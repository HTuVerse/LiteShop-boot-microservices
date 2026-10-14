package org.hongtu.config.DAO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;
import org.hongtu.config.DTO.AiModelConfigRecord;
/** 平台 AI 配置持久化，版本号防止多个管理员互相覆盖。 */
@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface AiModelConfigMapper {
    @Select("SELECT revision, enabled, base_url AS baseUrl, model_name AS model, api_key_ciphertext AS encryptedApiKey FROM sys_ai_model_config WHERE id = 1")
    AiModelConfigRecord read();
    @Update("UPDATE sys_ai_model_config SET enabled = #{config.enabled}, base_url = #{config.baseUrl}, " +
            "model_name = #{config.model}, api_key_ciphertext = #{config.encryptedApiKey}, revision = revision + 1, " +
            "updated_by = #{userId}, updated_at = CURRENT_TIMESTAMP WHERE id = 1 AND revision = #{config.revision}")
    int save(@Param("config") AiModelConfigRecord config, @Param("userId") long userId);
}
