package com.example.demo.service.impl;

import com.example.demo.domain.entity.User;
import com.example.demo.repository.UserMapper;
import com.example.common.web.PageResult;
import com.example.demo.domain.dto.UserDTO;
import com.example.demo.domain.query.UserPageQueryVO;
import com.example.demo.domain.query.UserQueryVO;
import com.example.demo.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author mini-spring-cloud-project
 * @since 2026-10-24
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /** 按主键查询详情（主键装进 QueryVO 复用 list，SQL 见 UserMapper.xml） */
    @Override
    public UserDTO detail(Long id) {
        UserQueryVO queryVO = new UserQueryVO();
        queryVO.setId(id);
        List<UserDTO> records = list(queryVO);
        return records.isEmpty() ? null : records.get(0);
    }

    /** 软删除：显式 UPDATE（SQL 见 UserMapper.xml），返回是否删除成功 */
    @Override
    public boolean remove(Long id) {
        return baseMapper.softDeleteById(id) > 0;
    }

    /** 条件查询列表（SQL 见 UserMapper.xml） */
    @Override
    public List<UserDTO> list(UserQueryVO queryVO) {
        return baseMapper.selectListByCondition(queryVO);
    }

    /** 条件分页查询（SQL 见 UserMapper.xml，分页由 MyBatis-Plus 插件处理） */
    @Override
    public PageResult<UserDTO> page(UserPageQueryVO queryVO) {
        Page<UserDTO> page = new Page<UserDTO>(queryVO.getPageNum(), queryVO.getPageSize());
        IPage<UserDTO> result = baseMapper.selectPageByCondition(page, queryVO);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}