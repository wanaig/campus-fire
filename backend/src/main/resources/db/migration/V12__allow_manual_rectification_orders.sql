-- 支持管理员手工创建整改单：巡检记录与任务编号允许为空
ALTER TABLE rectification_order MODIFY record_id VARCHAR(36) NULL;
ALTER TABLE rectification_order MODIFY task_id BIGINT NULL;
