package com.example.demo.controller;

import com.example.common.web.ApiResponse;
import com.example.common.web.PageResult;
import com.example.demo.convert.UserConverter;
import com.example.demo.domain.dto.UserDTO;
import com.example.demo.domain.query.UserPageQueryVO;
import com.example.demo.domain.query.UserQueryVO;
import com.example.demo.domain.entity.User;
import com.example.demo.service.UserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 用户表 前端控制器
 * </p>
 *
 * @author mini-spring-cloud-project
 * @since 2026-10-24
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /** 新增 */
    @PostMapping("/save")
    public ApiResponse<UserDTO> save(@RequestBody UserDTO dto) {
        User entity = UserConverter.INSTANCE.toEntity(dto);
        userService.save(entity);
        return ApiResponse.ok(UserConverter.INSTANCE.toDTO(entity));
    }

    /** 修改 */
    @PutMapping("/{id}")
    public ApiResponse<UserDTO> update(@PathVariable Long id, @RequestBody UserDTO dto) {
        dto.setId(id);
        userService.updateById(UserConverter.INSTANCE.toEntity(dto));
        return ApiResponse.ok(UserConverter.INSTANCE.toDTO(userService.getById(id)));
    }

    /**
     * 软删除（默认行为）：转调 service.remove，内部是 UserMapper.xml 里的显式 UPDATE，
     * 只把删除标记置 1，行保留在库里、后续查询自动过滤掉；不发 DELETE，
     * 也不用 removeById / updateById（updateById 会把逻辑删除列从 SET 子句里剔除，生成非法 SQL）。
     * 确实需要物理删除时另外写 SQL 或自定义 mapper 方法，不要改这里。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> remove(@PathVariable Long id) {
        return ApiResponse.ok(userService.remove(id));
    }

    /** 详情（查询逻辑在 service.detail 里，主键装进 QueryVO 走 list） */
    @GetMapping("/{id}")
    public ApiResponse<UserDTO> detail(@PathVariable Long id) {
        return ApiResponse.ok(userService.detail(id));
    }

    /** 列表查询（条件组装、查询、实体转 DTO 都在 service 层） */
    @GetMapping("/list")
    public ApiResponse<List<UserDTO>> list(UserQueryVO query) {
        return ApiResponse.ok(userService.list(query));
    }

    /** 分页查询（条件组装、查询、实体转 DTO 都在 service 层） */
    @GetMapping("/page")
    public ApiResponse<PageResult<UserDTO>> page(UserPageQueryVO query) {
        return ApiResponse.ok(userService.page(query));
    }
}