package com.example.order.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单视图：订单信息 + 通过 OpenFeign 从用户服务取回的用户信息。
 */
@Getter
@AllArgsConstructor
public class OrderView {

    private final Long orderId;

    private final String orderNo;

    /** 通过 OpenFeign 调用 user-service 得到的用户，取不到时为 null */
    private final UserView user;
}