package com.example.common.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 统一 API 响应包装。code = 0 表示成功，其他值等于 HTTP 状态码。
 *
 * <p>位于共享模块，供各服务在 OpenFeign 调用中复用同一份契约。
 *
 * <p>构造器上的 {@code @JsonCreator} / {@code @JsonProperty} 不是装饰：
 * 本类字段是 final 且构造器私有，服务间调用时消费端需要把响应体反序列化回来。
 * 不显式标注的话，Jackson 只能靠「-parameters 编译参数 + 参数名与字段名一致」
 * 这条隐式链路才能反序列化，一旦编译参数丢失就会在运行时报
 * "cannot deserialize from Object value"，排查起来很费劲。
 */
@Getter
public class ApiResponse<T> {

    public static final int SUCCESS_CODE = 0;

    private final int code;
    private final String message;
    private final T data;

    @JsonCreator
    private ApiResponse(@JsonProperty("code") int code,
                        @JsonProperty("message") String message,
                        @JsonProperty("data") T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<T>(SUCCESS_CODE, "ok", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<T>(code, message, null);
    }
}