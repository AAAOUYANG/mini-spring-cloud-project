-- 由 spring.sql.init 在应用启动时自动执行。
-- 数据库本身由 JDBC URL 参数 createDatabaseIfNotExist=true 创建。
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`        VARCHAR(50)     NOT NULL COMMENT '登录名',
    `password`        VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密码散列',
    `nickname`        VARCHAR(50)              DEFAULT NULL COMMENT '昵称',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    `created_by`      BIGINT UNSIGNED          DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(50)              DEFAULT NULL COMMENT '创建人名称',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by`      BIGINT UNSIGNED          DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(50)              DEFAULT NULL COMMENT '更新人名称',
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户表';