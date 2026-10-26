package com.example.order.client;

import com.example.common.web.ApiResponse;
import com.example.order.domain.UserView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务的 Feign 客户端。
 *
 * <p>{@code name} 填的是 Nacos 里的服务名（对应 user-service 的
 * {@code spring.application.name}），不是主机名或 IP：真正的地址由
 * Spring Cloud LoadBalancer 从注册中心取实例列表后解析。
 *
 * <p>{@code path} 与 user-service 控制器的 {@code @RequestMapping("/user")} 对齐。
 *
 * <p>返回类型复用共享模块的 {@code ApiResponse}，data 用的是本模块自己的
 * {@link UserView}——服务之间靠 JSON 契约交互，不共享 DTO 类，避免一个小改动
 * 牵动所有服务一起升级。
 */
@FeignClient(name = "mini-user-service", path = "/user")
public interface UserClient {

    /**
     * 查询用户详情。对应 user-service 的 {@code GET /user/{id}}。
     *
     * <p>{@code @PathVariable} 必须显式写名字：Feign 反射生成请求时拿不到
     * 方法的参数名，省掉名字会直接报错。
     */
    @GetMapping("/{id}")
    ApiResponse<UserView> detail(@PathVariable("id") Long id);
}