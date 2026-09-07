package org.hongtu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 餐厅服务启动类（菜品/门店/餐桌/订单/打印/Netty WebSocket/订单超时任务）
 */
@SpringBootApplication(scanBasePackages = "org.hongtu")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "org.hongtu.api.client")
public class RestaurantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantServiceApplication.class, args);
    }
}
