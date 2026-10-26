package com.example.demo.controller;

import com.example.common.web.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配置中心演示端点：返回 Nacos 里 {@code mini-common.yaml} 的 {@code mini.demo.message}。
 *
 * <p>用来验证「配置改动无需重启即可生效」：改掉 Nacos 上这个值后，再次请求本接口会立刻返回新值。
 * SCA 的 nacos-config 默认开启了配置监听与自动刷新（{@code refresh-enabled=true}），
 * {@link RefreshScope} 标注的 Bean 会在配置变化后被重建，因此能读到新值。
 * 若自动刷新被关掉，也可以手动触发：{@code curl -X POST http://127.0.0.1:8081/actuator/refresh}。
 *
 * <p>真实项目里这类演示接口应当删掉——它只是为了把「配置中心确实在工作」变成可观测的事实。
 */
@RestController
@RequestMapping("/config")
@RefreshScope
public class ConfigDemoController {

    @Value("${mini.demo.message:(配置中心里没有 mini.demo.message，用的是本地默认值)}")
    private String message;

    /** {@code GET /config/demo}（直连本服务，不经过网关） */
    @GetMapping("/demo")
    public ApiResponse<String> demo() {
        return ApiResponse.ok(message);
    }
}