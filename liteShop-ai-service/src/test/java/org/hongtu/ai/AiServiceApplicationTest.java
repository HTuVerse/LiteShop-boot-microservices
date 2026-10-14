package org.hongtu.ai;

import cn.dev33.satoken.stp.StpInterface;
import org.hongtu.ai.application.AiManagerService;
import org.hongtu.tenant.TenantAccessValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 不连接配置中心、模型或数据库的 AI 服务启动装配测试。 */
@SpringBootTest(properties = {
        "spring.config.location=optional:classpath:ai-manager-startup-test.yml",
        "spring.application.name=ai-manager-startup-test",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.rabbitmq.dynamic=false",
        "liteshop.internal-call.secret=ai-manager-startup-test"
})
class AiServiceApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void startsWithSharedAuthenticationAndNoBusinessDataSource() {
        assertNotNull(context.getBean(AiManagerService.class));
        assertNotNull(context.getBean(ObjectMapper.class));
        assertNotNull(context.getBean(StpInterface.class));
        assertNotNull(context.getBean(TenantAccessValidator.class));
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
        assertTrue(context.getBeansOfType(org.springframework.ai.chat.model.ChatModel.class).isEmpty());
    }
}
