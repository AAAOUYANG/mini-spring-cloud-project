DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50) NOT NULL,
    password        VARCHAR(100) NOT NULL,
    nickname        VARCHAR(50),
    status          TINYINT NOT NULL DEFAULT 1,
    deleted         TINYINT NOT NULL DEFAULT 0,
    created_by      BIGINT,
    created_by_name VARCHAR(50),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_by_name VARCHAR(50),
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);
