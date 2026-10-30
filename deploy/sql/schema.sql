-- AI PR Review 平台数据库初始化脚本（全新安装）
-- 适用：MySQL 8.x / 9.x / InnoDB / utf8mb4
--
-- 账号模型说明：
--   当前骨架统一使用 user 作为登录账号表，业务表 user_setting、analysis_task
--   的 user_id 外键都指向 user.id。
--   如果数据库使用过旧版账号表，请先执行
--   deploy/sql/migration-20261023-rename-sys-user-to-user.sql。
--
-- 脚本只创建不存在的表，不会删除已有数据；需要重置时请先备份再手动删表。

CREATE DATABASE IF NOT EXISTS `ai-pr-review`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `ai-pr-review`;

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1. 系统用户
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`        VARCHAR(50)  NOT NULL COMMENT '登录名',
    `password`        VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
    `password_set`    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否已设置可用密码：1 是，0 否（GitHub 创建）',
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
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_status` (`status`),
    KEY `idx_user_role` (`role`),
    KEY `idx_user_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '系统用户表';

-- 种子账号：admin / admin123，demo / demo123
-- 密码为 BCrypt 哈希，禁止在数据库中保存明文密码。
INSERT INTO `user` (`username`, `password`, `nickname`, `email`, `role`, `status`, `deleted`)
VALUES ('admin', '$2a$10$eDT7OPiDbAOrKEWKFS/AHOCZCL/xWPTVGOtGv/JOTKISDxSCeotYK',
        '系统管理员', 'admin@example.com', 'ADMIN', 1, 0)
ON DUPLICATE KEY UPDATE `username` = `username`;

INSERT INTO `user` (`username`, `password`, `nickname`, `email`, `role`, `status`, `deleted`)
VALUES ('demo', '$2a$10$UP4ZnXtul2Wl9r.PIqb20.dKoeDdbFcN0s4ONPqrGts6UD4cxUH0q',
        '演示用户', 'demo@example.com', 'USER', 1, 0)
ON DUPLICATE KEY UPDATE `username` = `username`;

-- ------------------------------------------------------------
-- 2. GitHub 账号绑定
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_github_binding`
(
    `user_id`                 BIGINT       NOT NULL COMMENT '本地用户 ID，关联 user.id',
    `github_id`               BIGINT       NOT NULL COMMENT 'GitHub 数字用户 ID，唯一标识',
    `github_login`            VARCHAR(64)  NOT NULL COMMENT 'GitHub 登录名，仅作展示，可变',
    `avatar_url`              VARCHAR(500)          DEFAULT NULL COMMENT 'GitHub 头像地址',
    `email`                   VARCHAR(255)          DEFAULT NULL COMMENT 'GitHub 主邮箱，仅作展示，不作为唯一键',
    `access_token_ciphertext` TEXT         NOT NULL COMMENT 'AES-GCM 加密后的 GitHub access token',
    `scope`                   VARCHAR(255)          DEFAULT NULL COMMENT 'GitHub 返回的授权 scope',
    `token_status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '令牌状态：1 有效，2 失效，3 已撤销',
    `authorized_at`           DATETIME     NOT NULL COMMENT '最近一次授权时间',
    `last_validated_at`       DATETIME              DEFAULT NULL COMMENT '最近一次校验时间',
    `create_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_github_id` (`github_id`),
    KEY `idx_token_status` (`token_status`),
    CONSTRAINT `fk_binding_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '用户 GitHub 账号绑定';

-- ------------------------------------------------------------
-- 3. 管理员模型配置
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_model_config`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `display_name`       VARCHAR(100) NOT NULL COMMENT '显示名称',
    `provider`           VARCHAR(32)  NOT NULL COMMENT '请求格式：OPENAI_CHAT / ANTHROPIC_MESSAGES',
    `base_url`           VARCHAR(500) NOT NULL COMMENT 'API 根地址，含版本前缀（如 https://api.openai.com/v1）',
    `model_id`           VARCHAR(200) NOT NULL COMMENT '模型 ID',
    `api_key_ciphertext` TEXT         NOT NULL COMMENT 'AES-GCM 加密后的 API Key',
    `api_key_hint`       VARCHAR(16)           DEFAULT NULL COMMENT 'API Key 末 4 位掩码',
    `max_output_tokens`  INT                   DEFAULT NULL COMMENT '输出 token 上限；空表示使用 provider 默认值',
    `temperature`        DECIMAL(3, 2)         DEFAULT NULL COMMENT '温度；空表示使用默认值',
    `enabled`            TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：1 是，0 否',
    `is_default`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认模型：1 是，0 否',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_enabled_default` (`enabled`, `is_default`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '管理员模型配置';

-- ------------------------------------------------------------
-- 4. 用户设置
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_setting`
(
    `user_id`          BIGINT        NOT NULL COMMENT '用户 ID，关联 user.id',
    `theme`            TINYINT       NOT NULL DEFAULT 3 COMMENT '主题：1 浅色，2 深色，3 跟随系统',
    `language`         VARCHAR(10)   NOT NULL DEFAULT 'zh-CN' COMMENT '界面语言：zh-CN 中文，en-US 英文',
    `check_categories` VARCHAR(200)  NOT NULL DEFAULT '1,2,3,4,5,6' COMMENT '重点检查类别，逗号分隔：1 逻辑 2 边界 3 异常 4 权限 5 兼容 6 测试 7 其他',
    `ignore_dirs`      VARCHAR(1000) NOT NULL DEFAULT 'node_modules,dist,target,.git' COMMENT '忽略目录，逗号分隔',
    `max_file_count`   INT           NOT NULL DEFAULT 200 COMMENT '单次分析最大文件数',
    `max_diff_lines`   INT           NOT NULL DEFAULT 20000 COMMENT '单次分析最大 Diff 行数',
    `create_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_setting_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '用户设置';

-- ------------------------------------------------------------
-- 3. Pull Request 元数据
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `pull_request`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `repo_full_name` VARCHAR(150) NOT NULL COMMENT '仓库全名，格式 owner/repo',
    `pr_number`      INT          NOT NULL COMMENT 'PR 编号',
    `title`          VARCHAR(300) NOT NULL COMMENT 'PR 标题',
    `author`         VARCHAR(64)  NOT NULL COMMENT 'PR 作者 GitHub 用户名',
    `state`          TINYINT      NOT NULL DEFAULT 1 COMMENT 'PR 状态：1 open，2 closed，3 merged',
    `base_branch`    VARCHAR(255) NOT NULL COMMENT '目标分支',
    `head_branch`    VARCHAR(255) NOT NULL COMMENT '来源分支',
    `head_sha`       CHAR(40)     NOT NULL COMMENT '最新 head commit SHA',
    `add_lines`      INT          NOT NULL DEFAULT 0 COMMENT '新增行数',
    `delete_lines`   INT          NOT NULL DEFAULT 0 COMMENT '删除行数',
    `file_count`     INT          NOT NULL DEFAULT 0 COMMENT '变更文件数',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_repo_pr` (`repo_full_name`, `pr_number`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = 'GitHub Pull Request 元数据';

-- ------------------------------------------------------------
-- 4. 分析任务
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `analysis_task`
(
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `pr_id`           BIGINT      NOT NULL COMMENT '关联 pull_request.id',
    `user_id`         BIGINT      NULL COMMENT '提交用户 ID，关联 user.id；游客任务为空',
    `head_sha`        CHAR(40)    NOT NULL COMMENT '本次分析对应的 head commit SHA',
    `report_language` VARCHAR(10) NOT NULL DEFAULT 'zh-CN' COMMENT '报告语言：zh-CN 中文，en-US 英文',
    `status`          TINYINT     NOT NULL DEFAULT 1 COMMENT '任务状态：1 排队，2 分析中，3 已完成，4 部分完成，5 失败，6 已取消',
    `stage`           TINYINT     NOT NULL DEFAULT 1 COMMENT '当前阶段：1 排队，2 获取 PR，3 获取变更，4 补充上下文，5 AI 分析，6 校验入库，7 结束',
    `progress`        TINYINT     NOT NULL DEFAULT 0 COMMENT '进度，0-100',
    `model_name`      VARCHAR(64) NULL COMMENT '本次使用的模型名称',
    `analysis_config` JSON        NULL COMMENT '本次分析使用的非敏感配置快照',
    `error_code`      TINYINT     NULL COMMENT '失败或部分完成的原因编码，正常为空',
    `error_message`   VARCHAR(500) NULL COMMENT '失败或部分完成的原因摘要，已脱敏',
    `guest_token`     VARCHAR(64) NULL COMMENT '游客访问令牌，32 字节随机数的十六进制串，登录用户为空',
    `retry_of_task_id` BIGINT     NULL COMMENT '重试来源任务 ID',
    `expire_time`     DATETIME    NULL COMMENT '游客任务过期时间，登录用户为空',
    `start_time`      DATETIME    NULL COMMENT '开始执行时间',
    `finish_time`     DATETIME    NULL COMMENT '结束时间',
    `is_deleted`      TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_guest_token` (`guest_token`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_user_pr_time` (`user_id`, `pr_id`, `create_time`),
    KEY `idx_pr_sha_user` (`pr_id`, `head_sha`, `user_id`, `status`),
    KEY `idx_status` (`status`),
    KEY `idx_expire_time` (`expire_time`),
    KEY `idx_retry_of_task` (`retry_of_task_id`),
    CONSTRAINT `fk_task_pr` FOREIGN KEY (`pr_id`) REFERENCES `pull_request` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_task_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = 'PR 分析任务';

-- ------------------------------------------------------------
-- 5. 分析报告总结
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `analysis_report`
(
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id`     BIGINT   NOT NULL COMMENT '关联 analysis_task.id',
    `purpose`     TEXT     NULL COMMENT 'PR 修改目的',
    `changes`     TEXT     NULL COMMENT '主要变更',
    `impacts`     TEXT     NULL COMMENT '可能影响的模块',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task` (`task_id`),
    CONSTRAINT `fk_report_task` FOREIGN KEY (`task_id`) REFERENCES `analysis_task` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '分析报告总结';

-- ------------------------------------------------------------
-- 6. 任务变更文件
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `task_file`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id`         BIGINT       NOT NULL COMMENT '关联 analysis_task.id',
    `file_path`       VARCHAR(512) NOT NULL COMMENT '当前文件路径',
    `old_path`        VARCHAR(512) NULL COMMENT '重命名前的文件路径',
    `change_type`     TINYINT      NOT NULL COMMENT '变更类型：1 新增，2 修改，3 删除，4 重命名，5 复制',
    `add_lines`       INT          NOT NULL DEFAULT 0 COMMENT '新增行数',
    `delete_lines`    INT          NOT NULL DEFAULT 0 COMMENT '删除行数',
    `language`        VARCHAR(32)  NULL COMMENT '文件语言，如 java、typescript、python',
    `is_binary`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否二进制文件：0 否，1 是',
    `patch`           MEDIUMTEXT   NULL COMMENT '文件 diff 片段；二进制或 GitHub 未返回时为空',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '分析状态：1 待分析，2 已分析，3 已跳过，4 分析失败',
    `skip_reason`     VARCHAR(500) NULL COMMENT '跳过或失败原因',
    `priority_reason` VARCHAR(500) NULL COMMENT '重点阅读原因；为空表示非重点文件',
    `summary`         TEXT         NULL COMMENT '文件级变更简述',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_path` (`task_id`, `file_path`),
    KEY `idx_task_status` (`task_id`, `status`),
    CONSTRAINT `fk_file_task` FOREIGN KEY (`task_id`) REFERENCES `analysis_task` (`id`) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '任务变更文件';

-- ------------------------------------------------------------
-- 7. 评审问题与人工反馈
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `review_issue`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id`           BIGINT       NOT NULL COMMENT '关联 analysis_task.id',
    `file_id`           BIGINT       NULL COMMENT '关联 task_file.id；报告级提示为空',
    `title`             VARCHAR(300) NOT NULL COMMENT '问题标题',
    `category`          TINYINT      NOT NULL COMMENT '问题类别：1 逻辑错误，2 空值与边界，3 异常处理，4 权限与敏感信息，5 接口与数据兼容，6 缺失测试，7 其他',
    `severity`          TINYINT      NOT NULL COMMENT '严重度：1 低，2 中，3 高，4 严重',
    `confidence`        TINYINT      NOT NULL COMMENT '置信度，0-100',
    `start_line`        INT          NULL COMMENT '起始行号；报告级提示为空',
    `end_line`          INT          NULL COMMENT '结束行号；单行问题可为空或与起始行相同',
    `trigger_condition` TEXT         NOT NULL COMMENT '触发条件',
    `impact`            TEXT         NOT NULL COMMENT '潜在影响',
    `evidence`          TEXT         NULL COMMENT '证据片段；证据不足时为空',
    `ai_suggestion`     TEXT         NOT NULL COMMENT '模型原始建议',
    `suggestion`        TEXT         NOT NULL COMMENT '当前建议，人工编辑后覆盖',
    `feedback_status`   TINYINT      NOT NULL DEFAULT 1 COMMENT '处理状态：1 待确认，2 已采纳，3 已解决，4 误报',
    `feedback_remark`   VARCHAR(500) NULL COMMENT '人工处理理由',
    `feedback_user_id`  BIGINT       NULL COMMENT '最后反馈操作人 ID，关联 user.id；游客为空',
    `feedback_time`     DATETIME     NULL COMMENT '最后反馈操作时间',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_feedback` (`task_id`, `feedback_status`, `severity`),
    KEY `idx_file` (`file_id`),
    KEY `idx_feedback_user` (`feedback_user_id`),
    CONSTRAINT `fk_issue_task` FOREIGN KEY (`task_id`) REFERENCES `analysis_task` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_issue_file` FOREIGN KEY (`file_id`) REFERENCES `task_file` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_issue_feedback_user` FOREIGN KEY (`feedback_user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '评审问题与人工反馈';
