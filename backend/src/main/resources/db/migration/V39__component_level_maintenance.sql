-- 维护保养记录升级为部件级：maintenance_record 记录对某个部件的保养(MAINTAIN)或更换(REPLACE)，
-- 不再由巡检提交自动生成设施级 ROUTINE 记录（巡检与保养分离）。
-- component_code/component_name 可空：兼容存量设施级记录。
ALTER TABLE maintenance_record
    ADD COLUMN component_code VARCHAR(64) NULL AFTER maintenance_type,
    ADD COLUMN component_name VARCHAR(255) NULL AFTER component_code;

-- 存量巡检自动生成的记录语义并入日常维护，不区分部件
UPDATE maintenance_record SET maintenance_type = 'MAINTAIN' WHERE maintenance_type = 'ROUTINE';
