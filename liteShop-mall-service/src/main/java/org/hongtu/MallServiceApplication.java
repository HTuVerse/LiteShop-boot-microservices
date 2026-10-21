package org.hongtu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 积分兑换商城服务启动类。 */
@SpringBootApplication(scanBasePackages = "org.hongtu")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "org.hongtu.api.client")
@EnableScheduling
public class MallServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MallServiceApplication.class, args);
    }
}
