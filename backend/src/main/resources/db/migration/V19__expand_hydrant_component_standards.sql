ALTER TABLE inspection_item
    ADD COLUMN inspection_standard VARCHAR(255) NOT NULL DEFAULT '' AFTER item_name;

UPDATE inspection_item
SET inspection_standard = item_name
WHERE inspection_standard = '';

UPDATE inspection_item item
JOIN facility_type type ON type.id = item.facility_type_id
SET item.item_name = CASE item.item_code
        WHEN 'CABINET' THEN '箱门'
        WHEN 'HOSE' THEN '水带'
        WHEN 'NOZZLE' THEN '枪头'
        WHEN 'BUTTON' THEN '按钮'
        WHEN 'VALVE' THEN '阀门'
        WHEN 'EXTINGUISHER' THEN '灭火器'
        ELSE item.item_name
    END,
    item.inspection_standard = CASE item.item_code
        WHEN 'CABINET' THEN '箱门完好、开启正常，玻璃和标识清晰'
        WHEN 'HOSE' THEN '水带在位、盘放整齐，无破损和霉变'
        WHEN 'NOZZLE' THEN '枪头及接口齐全、完好，无锈蚀'
        WHEN 'BUTTON' THEN '消火栓按钮完好，标识清晰'
        WHEN 'VALVE' THEN '阀门启闭正常，无锈蚀和渗漏'
        WHEN 'EXTINGUISHER' THEN '箱内灭火器在位，压力和外观状态正常'
        ELSE item.inspection_standard
    END,
    item.required_flag = 1,
    item.enabled = 1
WHERE type.type_code = 'FIRE_HYDRANT'
  AND item.item_code IN ('CABINET','HOSE','NOZZLE','BUTTON','VALVE','EXTINGUISHER');
