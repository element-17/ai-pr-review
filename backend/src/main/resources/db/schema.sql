-- ============================================================
-- AI PR Review 平台数据库初始化脚本
-- 数据库：ai-pr-review
-- 说明：
--   1. 所有表使用 InnoDB + utf8mb4；
--   2. sys_user 使用 deleted 字段做逻辑删除；
--   3. create_time / update_time 由数据库默认值兜底，
--      业务写入时由 MyBatis-Plus 自动填充覆盖；
--   4. 种子账号仅用于本地开发，上线前必须修改密码。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ai-pr-review`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `ai-pr-review`;

CREATE TABLE IF NOT EXISTS `sys_user`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`        VARCHAR(50)  NOT NULL COMMENT '登录名',
    `password`        VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
    `nickname`        VARCHAR(50)           DEFAULT NULL COMMENT '昵称',
    `email`           VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
    `phone`           VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    `avatar`          VARCHAR(255)          DEFAULT NULL COMMENT '头像地址',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    `role`            VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN / USER',
    `last_login_time` DATETIME              DEFAULT NULL COMMENT '最后登录时间',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_user`     BIGINT                DEFAULT NULL COMMENT '创建人 ID',
    `update_user`     BIGINT                DEFAULT NULL COMMENT '更新人 ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`),
    KEY `idx_sys_user_status` (`status`),
    KEY `idx_sys_user_role` (`role`),
    KEY `idx_sys_user_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '系统用户表';

-- 种子账号：admin / admin123，demo / demo123
-- 密码为 BCrypt 哈希，禁止在数据库中保存明文密码。
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `email`, `role`, `status`, `deleted`)
VALUES ('admin', '$2a$10$eDT7OPiDbAOrKEWKFS/AHOCZCL/xWPTVGOtGv/JOTKISDxSCeotYK',
        '系统管理员', 'admin@example.com', 'ADMIN', 1, 0)
ON DUPLICATE KEY UPDATE `username` = `username`;

INSERT INTO `sys_user` (`username`, `password`, `nickname`, `email`, `role`, `status`, `deleted`)
VALUES ('demo', '$2a$10$UP4ZnXtul2Wl9r.PIqb20.dKoeDdbFcN0s4ONPqrGts6UD4cxUH0q',
        '演示用户', 'demo@example.com', 'USER', 1, 0)
ON DUPLICATE KEY UPDATE `username` = `username`;
