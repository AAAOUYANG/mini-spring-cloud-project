package com.example.order.controller;

import com.example.common.web.ApiResponse;
import com.example.order.client.UserClient;
import com.example.order.domain.OrderView;
import com.example.order.domain.UserView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 订单接口。订单数据是本地构造的（本模块不连库），
 * 用户信息通过 OpenFeign 从 user-service 获取，用来演示服务间调用链路。
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Resource
    private UserClient userClient;

    /**
     * 订单详情：{@code GET /api/order/1001?userId=1}（经网关）或
     * {@code GET /order/1001?userId=1}（直连本服务）。
     */
    @GetMapping("/{orderId}")
    public ApiResponse<OrderView> detail(@PathVariable Long orderId, @RequestParam Long userId) {
        ApiResponse<UserView> response = userClient.detail(userId);
        UserView user = response == null ? null : response.getData();
        return ApiResponse.ok(new OrderView(orderId, "NO-" + orderId, user));
    }
}