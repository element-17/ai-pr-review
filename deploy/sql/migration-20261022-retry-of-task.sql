-- 增加任务重试来源字段，用于 F03/F17 手动重试溯源
USE `ai-pr-review`;

ALTER TABLE `analysis_task`
    ADD COLUMN `retry_of_task_id` BIGINT NULL COMMENT '重试来源任务 ID' AFTER `guest_token`,
    ADD KEY `idx_retry_of_task` (`retry_of_task_id`);
