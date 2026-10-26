package com.example.demo.domain.query;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户表 列表查询条件（字段与实体一一对应）
 * </p>
 *
 * @author mini-spring-cloud-project
 * @since 2026-10-24
 */
@Data
public class UserQueryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 登录名 */
    private String username;

    /** BCrypt 密码散列 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 状态：1 启用，0 禁用 */
    private Integer status;

    /** 逻辑删除：0 未删除，1 已删除 */
    private Integer deleted;

    /** 创建人ID */
    private Long createdBy;

    /** 创建人名称 */
    private String createdByName;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新人ID */
    private Long updatedBy;

    /** 更新人名称 */
    private String updatedByName;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}