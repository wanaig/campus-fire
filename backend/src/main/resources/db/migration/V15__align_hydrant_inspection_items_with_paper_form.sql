UPDATE inspection_item item
JOIN facility_type type ON type.id = item.facility_type_id
SET item.item_name = '箱门完好、开启正常',
    item.required_flag = 1,
    item.sort_order = 10,
    item.enabled = 1
WHERE type.type_code = 'FIRE_HYDRANT'
  AND item.item_code = 'CABINET';

UPDATE inspection_item item
JOIN facility_type type ON type.id = item.facility_type_id
SET item.enabled = 0
WHERE type.type_code = 'FIRE_HYDRANT'
  AND item.item_code = 'HOSE_VALVE';

INSERT INTO inspection_item (facility_type_id, item_code, item_name, required_flag, sort_order, enabled)
SELECT id, 'HOSE', '水带完好、无破损霉变', 1, 20, 1
FROM facility_type WHERE type_code = 'FIRE_HYDRANT'
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), required_flag = 1, sort_order = 20, enabled = 1;

INSERT INTO inspection_item (facility_type_id, item_code, item_name, required_flag, sort_order, enabled)
SELECT id, 'NOZZLE', '栓头和接口完好、无锈蚀', 1, 30, 1
FROM facility_type WHERE type_code = 'FIRE_HYDRANT'
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), required_flag = 1, sort_order = 30, enabled = 1;

INSERT INTO inspection_item (facility_type_id, item_code, item_name, required_flag, sort_order, enabled)
SELECT id, 'BUTTON', '消火栓按钮完好、标识清晰', 1, 40, 1
FROM facility_type WHERE type_code = 'FIRE_HYDRANT'
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), required_flag = 1, sort_order = 40, enabled = 1;

INSERT INTO inspection_item (facility_type_id, item_code, item_name, required_flag, sort_order, enabled)
SELECT id, 'VALVE', '阀门完好、无渗漏', 1, 50, 1
FROM facility_type WHERE type_code = 'FIRE_HYDRANT'
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), required_flag = 1, sort_order = 50, enabled = 1;

INSERT INTO inspection_item (facility_type_id, item_code, item_name, required_flag, sort_order, enabled)
SELECT id, 'EXTINGUISHER', '箱内灭火器在位且状态正常', 1, 60, 1
FROM facility_type WHERE type_code = 'FIRE_HYDRANT'
ON DUPLICATE KEY UPDATE item_name = VALUES(item_name), required_flag = 1, sort_order = 60, enabled = 1;
