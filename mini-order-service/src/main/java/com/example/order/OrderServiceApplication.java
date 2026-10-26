package com.example.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 订单服务。本模块不连数据库，重点是演示「服务之间用 OpenFeign 调用」：
 * 它通过 {@code lb://mini-user-service} 调用用户服务，地址由 Nacos + LoadBalancer 解析。
 */
@SpringBootApplication
@EnableFeignClients
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}