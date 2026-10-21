package org.hongtu;

import org.hongtu.api.client.RestaurantFeignClient;
import org.hongtu.api.client.ConfigFeignClient;
import org.hongtu.api.client.FileResourceClient;
import org.hongtu.api.client.PayFeignClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 用户服务启动类（用户/角色/权限/积分/余额）
 * 角色权限数据加载 StpInterfaceImpl 保留在本服务（查库实现）
 */
@SpringBootApplication(scanBasePackages = "org.hongtu")
@EnableDiscoveryClient
@EnableFeignClients(clients = {RestaurantFeignClient.class, ConfigFeignClient.class, FileResourceClient.class, PayFeignClient.class})
@EnableAspectJAutoProxy
@EnableScheduling
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
