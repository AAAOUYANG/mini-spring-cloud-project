package com.example.demo.service;

import com.example.demo.domain.entity.User;
import com.example.common.web.PageResult;
import com.example.demo.domain.dto.UserDTO;
import com.example.demo.domain.query.UserPageQueryVO;
import com.example.demo.domain.query.UserQueryVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author mini-spring-cloud-project
 * @since 2026-10-24
 */
public interface UserService extends IService<User> {

    /**
     * 按主键查询详情（内部走 list，条件与 SQL 见 UserMapper.xml）
     *
     * @param id 主键
     * @return DTO，查不到时返回 null
     */
    UserDTO detail(Long id);

    /**
     * 软删除：显式 UPDATE 把删除标记置 1，行保留在库里，后续查询自动过滤。
     * 不走 MyBatis-Plus 的 removeById / deleteById，SQL 见 UserMapper.xml。
     *
     * @param id 主键
     * @return 是否删除成功（记录不存在或已删除返回 false）
     */
    boolean remove(Long id);

    /**
     * 条件查询列表
     *
     * @param queryVO 查询条件
     * @return DTO 列表
     */
    List<UserDTO> list(UserQueryVO queryVO);

    /**
     * 条件分页查询
     *
     * @param queryVO 查询条件（PageQueryVO 继承 QueryVO，额外带 pageNum / pageSize）
     * @return 分页结果，records 为 DTO
     */
    PageResult<UserDTO> page(UserPageQueryVO queryVO);
}