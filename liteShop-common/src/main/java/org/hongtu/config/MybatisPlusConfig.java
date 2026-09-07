package org.hongtu.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.hongtu.tenant.LiteShopTenantLineHandler;
import org.hongtu.tenant.StoreDataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置：分页插件、多租户插件（预留）、雪花 ID 生成器
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 多租户开关（预留）：置为 true 后自动为 SQL 追加 tenant_id 条件
     */
    @Value("${liteshop.tenant.enabled:true}")
    private boolean tenantEnabled;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 多租户插件必须在分页插件之前添加
        if (tenantEnabled) {
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new LiteShopTenantLineHandler()));
        }
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new StoreDataPermissionHandler()));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL)); // 如果配置多个插件, 切记分页最后添加
        return interceptor;
    }

    /**
     * 雪花 ID 生成器（IdType.ASSIGN_ID 使用）。
     * 多实例部署时通过环境变量为每个实例分配不同的 workerId / datacenterId（取值 0~31），
     * 避免不同实例生成重复 ID。
     */
    @Bean
    public IdentifierGenerator identifierGenerator(
            @Value("${liteshop.id.worker-id:1}") long workerId,
            @Value("${liteshop.id.datacenter-id:1}") long datacenterId) {
        return new DefaultIdentifierGenerator(workerId, datacenterId);
    }
}
