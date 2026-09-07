package org.hongtu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 文件服务启动类（文件上传、静态资源 /images/**）
 */
@SpringBootApplication(scanBasePackages = "org.hongtu")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "org.hongtu.api.client")
public class FileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileServiceApplication.class, args);
    }
}
