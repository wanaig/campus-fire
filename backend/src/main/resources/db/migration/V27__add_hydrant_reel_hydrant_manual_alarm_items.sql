-- 室内消火栓类型新增三个检查项：消防软管卷盘、消防栓（栓体）、消防手报（手动火灾报警按钮）。
-- item_code 沿用既有语义编码风格；新检查项在管理端新增时由系统按 ITEM-序号 自动生成编号。
INSERT INTO inspection_item (facility_type_id, item_code, item_name, inspection_standard, required_flag, sort_order, enabled)
SELECT id, 'HOSE_REEL', '消防软管卷盘', '卷盘固定牢固，卷管无破损老化，转动收放灵活，水枪齐全', 1, 70, 1
FROM facility_type WHERE type_code = 'FIRE_HYDRANT'
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), inspection_standard = VALUES(inspection_standard), required_flag = 1, sort_order = 70, enabled = 1;

INSERT INTO inspection_item (facility_type_id, item_code, item_name, inspection_standard, required_flag, sort_order, enabled)
SELECT id, 'HYDRANT', '消防栓', '栓口无锈蚀，闷盖齐全，阀体启闭正常、无渗漏', 1, 80, 1
FROM facility_type WHERE type_code = 'FIRE_HYDRANT'
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), inspection_standard = VALUES(inspection_standard), required_flag = 1, sort_order = 80, enabled = 1;

INSERT INTO inspection_item (facility_type_id, item_code, item_name, inspection_standard, required_flag, sort_order, enabled)
SELECT id, 'MANUAL_ALARM', '消防手报', '手动报警按钮外观完好，标识清晰，透明罩无破损', 1, 90, 1
FROM facility_type WHERE type_code = 'FIRE_HYDRANT'
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), inspection_standard = VALUES(inspection_standard), required_flag = 1, sort_order = 90, enabled = 1;
