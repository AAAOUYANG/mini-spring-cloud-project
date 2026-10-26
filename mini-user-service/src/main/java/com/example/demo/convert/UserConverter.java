package com.example.demo.convert;

import com.example.demo.domain.entity.User;
import com.example.demo.domain.dto.UserDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * <p>
 * 用户表 实体与 DTO 转换器
 * </p>
 *
 * @author mini-spring-cloud-project
 * @since 2026-10-24
 */
@Mapper(componentModel = "spring")
public interface UserConverter {

    UserConverter INSTANCE = Mappers.getMapper(UserConverter.class);

    /**
     * 实体 -> DTO
     *
     * @param entity 实体
     * @return DTO
     */
    UserDTO toDTO(User entity);

    /**
     * 实体列表 -> DTO 列表
     *
     * @param entities 实体列表
     * @return DTO 列表
     */
    List<UserDTO> toDTOList(List<User> entities);

    /**
     * DTO -> 实体
     *
     * @param dto DTO
     * @return 实体
     */
    @Mapping(target = "deleted", ignore = true)
    User toEntity(UserDTO dto);

    /**
     * DTO 列表 -> 实体列表
     *
     * @param dtos DTO 列表
     * @return 实体列表
     */
    List<User> toEntityList(List<UserDTO> dtos);
}