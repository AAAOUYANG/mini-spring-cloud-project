package com.example.demo.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 用户表 分页查询条件
 * </p>
 *
 * @author mini-spring-cloud-project
 * @since 2026-10-24
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageQueryVO extends UserQueryVO {

    /** 页码，从 1 开始 */
    private long pageNum = 1L;

    /** 每页条数 */
    private long pageSize = 10L;
}