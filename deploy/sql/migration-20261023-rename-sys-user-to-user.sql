-- 将旧版登录账号表重命名为 user，并重建业务表外键
-- 适用：已执行过旧版 deploy/sql/schema.sql（账号表名为 sys_user）的本地/测试库
-- 说明：
--   1. 执行前请备份，并确认 user_setting、analysis_task 中没有无法迁移的数据；
--   2. 全新安装直接执行 schema.sql 即可，不需要本脚本；
--   3. 如果库中已存在 user 表，请先确认数据归属并手动处理，避免 RENAME 失败；
--   4. 业务代码和 schema.sql 已统一使用 user，旧表名只保留在本迁移脚本中。

USE `ai-pr-review`;

ALTER TABLE `user_setting` DROP FOREIGN KEY `fk_setting_user`;
ALTER TABLE `analysis_task` DROP FOREIGN KEY `fk_task_user`;
ALTER TABLE `review_issue` DROP FOREIGN KEY `fk_issue_feedback_user`;

RENAME TABLE `sys_user` TO `user`;

ALTER TABLE `user_setting`
    ADD CONSTRAINT `fk_setting_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE;

ALTER TABLE `analysis_task`
    ADD CONSTRAINT `fk_task_user`
        FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT;

ALTER TABLE `review_issue`
    ADD CONSTRAINT `fk_issue_feedback_user`
        FOREIGN KEY (`feedback_user_id`) REFERENCES `user` (`id`) ON DELETE SET NULL;
