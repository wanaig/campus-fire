-- 巡检模式由「计划生成任务」改为「设施驱动」：保安扫码即可对设施巡检，
-- 不再依赖巡检计划/任务；历史数据按任务回填设施归属，任务相关字段转为可空留痕。
ALTER TABLE inspection_session
    MODIFY task_id BIGINT NULL,
    ADD COLUMN facility_id BIGINT NULL AFTER task_id,
    ADD KEY idx_session_facility (facility_id);
UPDATE inspection_session s
    JOIN inspection_task t ON t.id = s.task_id
    SET s.facility_id = t.facility_id
    WHERE s.facility_id IS NULL;

ALTER TABLE inspection_record
    MODIFY task_id BIGINT NULL,
    ADD COLUMN facility_id BIGINT NULL AFTER task_id,
    ADD KEY idx_record_facility (facility_id, submitted_at);
UPDATE inspection_record r
    JOIN inspection_task t ON t.id = r.task_id
    SET r.facility_id = t.facility_id
    WHERE r.facility_id IS NULL;

ALTER TABLE inspection_photo MODIFY task_id BIGINT NULL;
ALTER TABLE rectification_order MODIFY task_id BIGINT NULL;
