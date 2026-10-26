package com.example.demo.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户表 DTO
 * </p>
 *
 * @author mini-spring-cloud-project
 * @since 2026-10-24
 */
@Data
public class UserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 登录名 */
    private String username;

    /** BCrypt 密码散列 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 昵称 */
    private String nickname;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    /** 创建人ID */
    private Long createdBy;

    /** 创建人名称 */
    private String createdByName;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /** 更新人ID */
    private Long updatedBy;

    /** 更新人名称 */
    private String updatedByName;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}