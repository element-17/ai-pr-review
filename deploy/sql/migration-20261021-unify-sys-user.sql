-- 将业务表的用户外键从早期的 user 表统一到当前登录模型 sys_user
-- 适用：已执行过旧版 deploy/sql/schema.sql 的本地/测试库
-- 说明：
--   1. 执行前请确认 user_setting、analysis_task 中没有无法迁移的历史数据；
--   2. user 表在旧脚本中仅作为业务账号模型预留，当前骨架使用 sys_user；
--   3. 本脚本可重复执行的保护有限，生产环境请先备份。

USE `ai-pr-review`;

ALTER TABLE `user_setting`
    DROP FOREIGN KEY `fk_setting_user`;

ALTER TABLE `user_setting`
    ADD CONSTRAINT `fk_setting_user`
        FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE;

ALTER TABLE `analysis_task`
    DROP FOREIGN KEY `fk_task_user`;

ALTER TABLE `analysis_task`
    ADD CONSTRAINT `fk_task_user`
        FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT;

ALTER TABLE `review_issue`
    ADD COLUMN `feedback_user_id` BIGINT NULL COMMENT '最后反馈操作人 ID；游客为空' AFTER `feedback_remark`,
    ADD COLUMN `feedback_time` DATETIME NULL COMMENT '最后反馈操作时间' AFTER `feedback_user_id`;

ALTER TABLE `review_issue`
    ADD CONSTRAINT `fk_issue_feedback_user`
        FOREIGN KEY (`feedback_user_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL;

DROP TABLE IF EXISTS `user`;
