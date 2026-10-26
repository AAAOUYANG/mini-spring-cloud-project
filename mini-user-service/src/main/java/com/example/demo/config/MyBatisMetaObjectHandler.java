package com.example.demo.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 新增/更新时填充审计字段，使应用不依赖数据库默认值。
 *
 * <p>本示例还没有鉴权，所以操作人固定为系统账号。等接入鉴权后（Spring Security、
 * JWT claims 等），把 {@link #SYSTEM_USER_ID} / {@link #SYSTEM_USER_NAME} 换成真实登录用户。
 */
@Component
public class MyBatisMetaObjectHandler implements MetaObjectHandler {

    /** 鉴权接入前的占位操作人。 */
    public static final Long SYSTEM_USER_ID = 0L;
    public static final String SYSTEM_USER_NAME = "system";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createdBy", Long.class, SYSTEM_USER_ID);
        this.strictInsertFill(metaObject, "createdByName", String.class, SYSTEM_USER_NAME);
        this.strictInsertFill(metaObject, "updatedBy", Long.class, SYSTEM_USER_ID);
        this.strictInsertFill(metaObject, "updatedByName", String.class, SYSTEM_USER_NAME);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 这里用 setFieldValByName 而不是 strictUpdateFill：后者只在字段为 null 时填充，
        // 会保留从库里查出来的旧值。
        this.setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
        this.setFieldValByName("updatedBy", SYSTEM_USER_ID, metaObject);
        this.setFieldValByName("updatedByName", SYSTEM_USER_NAME, metaObject);
    }
}
