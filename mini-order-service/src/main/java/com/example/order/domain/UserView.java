package com.example.order.domain;

import lombok.Data;

/**
 * 用户视图（消费端自己的模型）。
 *
 * <p>刻意不引用 user-service 的 UserDTO：那样会让两个服务在编译期耦合，
 * 对方改字段就得一起重新构建。这里只声明自己真正用到的字段，
 * 对方多返回的字段会被 Jackson 忽略，少返回的字段为 null。
 *
 * <p>必须有默认构造器和 setter，Jackson 才能反序列化。
 */
@Data
public class UserView {

    private Long id;

    private String username;

    private String nickname;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    private String createdByName;
}