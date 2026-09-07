package org.hongtu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 抽奖服务启动类（活动/奖品/记录/次数）
 */
@SpringBootApplication(scanBasePackages = "org.hongtu")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "org.hongtu.api.client")
public class LotteryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LotteryServiceApplication.class, args);
    }
}
