-- 初始化数据库：MySQL 容器第一次启动（数据卷为空）时由 docker-entrypoint-initdb.d 自动执行
-- 执行顺序按文件名排序：01 -> 02（02 是 Nacos 的建表脚本）

CREATE DATABASE IF NOT EXISTS `mini_spring_boot`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS `nacos_config`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;