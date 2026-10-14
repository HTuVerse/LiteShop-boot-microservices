package org.hongtu.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.hongtu.tenant.LiteShopTenantLineHandler;
import org.hongtu.tenant.StoreDataPermissionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * MyBatis-Plus 配置：分页插件、多租户插件（预留）、雪花 ID 生成器
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 多租户隔离属于安全边界，生产配置缺失或误配时也不得关闭。
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new LiteShopTenantLineHandler()));
        // 门店数据权限始终注册；非门店受限账号由处理器直接放行。
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(new StoreDataPermissionHandler()));
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 防止客户端构造超大 pageSize 一次性拖垮数据库和应用内存。
        paginationInterceptor.setMaxLimit(200L);
        paginationInterceptor.setOverflow(false);
        interceptor.addInnerInterceptor(paginationInterceptor); // 如果配置多个插件, 切记分页最后添加
        return interceptor;
    }

    /**
     * 雪花 ID 生成器（IdType.ASSIGN_ID 使用）。
     * 多实例部署时通过环境变量为每个实例分配不同的 workerId / datacenterId（取值 0~31），
     * 避免不同实例生成重复 ID。
     */
    @Bean
    public IdentifierGenerator identifierGenerator(
            DataSource dataSource,
            @Value("${liteshop.id.lease-enabled:true}") boolean leaseEnabled,
            @Value("${liteshop.id.worker-id:0}") long workerId,
            @Value("${liteshop.id.datacenter-id:0}") long datacenterId) {
        if (leaseEnabled) {
            SnowflakeWorkerLease.Node node = SnowflakeWorkerLease.acquire(dataSource);
            return new DefaultIdentifierGenerator(node.workerId(), node.datacenterId());
        }
        if (workerId < 0 || workerId > 31 || datacenterId < 0 || datacenterId > 31) {
            throw new IllegalArgumentException("雪花ID worker-id/datacenter-id 必须在 0~31 之间");
        }
        return new DefaultIdentifierGenerator(workerId, datacenterId);
    }
}
