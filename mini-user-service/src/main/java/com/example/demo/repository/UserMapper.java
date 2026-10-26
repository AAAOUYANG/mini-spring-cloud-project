package com.example.demo.repository;

import com.example.demo.domain.entity.User;
import com.example.demo.domain.dto.UserDTO;
import com.example.demo.domain.query.UserQueryVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 用户表 Mapper 接口
 * </p>
 *
 * @author mini-spring-cloud-project
 * @since 2026-10-24
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 条件查询列表（SQL 见 UserMapper.xml，连表查询直接改 XML）
     *
     * @param queryVO 查询条件
     * @return DTO 列表
     */
    List<UserDTO> selectListByCondition(@Param("query") UserQueryVO queryVO);

    /**
     * 条件分页查询：IPage 由 MyBatis-Plus 的 PaginationInnerInterceptor 填充 total / records，
     * SQL 同样写在 UserMapper.xml 里。
     *
     * @param page    分页参数
     * @param queryVO 查询条件
     * @return DTO 分页结果
     */
    IPage<UserDTO> selectPageByCondition(IPage<UserDTO> page, @Param("query") UserQueryVO queryVO);

    /**
     * 软删除：显式 UPDATE 把删除标记置 1（SQL 见 UserMapper.xml），
     * 不走 MyBatis-Plus 的 deleteById / updateById。
     *
     * @param id 主键
     * @return 受影响行数，已删除或不存在时为 0
     */
    int softDeleteById(@Param("id") Long id);
}