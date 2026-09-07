package org.hongtu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 支付服务启动类（微信支付、支付宝）
 * 无独立数据库表，排除数据源自动配置
 */
@SpringBootApplication(scanBasePackages = "org.hongtu", exclude = {DataSourceAutoConfiguration.class})
@EnableDiscoveryClient
public class PayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayServiceApplication.class, args);
    }
}
