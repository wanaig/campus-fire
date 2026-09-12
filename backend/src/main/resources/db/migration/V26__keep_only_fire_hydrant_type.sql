-- 仅保留「室内消火栓」设施类型：删除其他类型（EXTINGUISHER、OTHER）及其关联数据。
-- 外键均为 RESTRICT，严格按子表在前、父表在后的顺序删除；
-- 若其他类型下存在设施档案，也一并清除其部件、二维码、巡检、维护、整改等全部关联数据。

-- 1) 其他类型设施档案的全部关联数据
DELETE fc FROM facility_component fc
    JOIN facility f ON f.id = fc.facility_id
    JOIN facility_type t ON t.id = f.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';
DELETE q FROM facility_qr_code q
    JOIN facility f ON f.id = q.facility_id
    JOIN facility_type t ON t.id = f.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';
DELETE sug FROM facility_action_suggestion sug
    JOIN facility f ON f.id = sug.facility_id
    JOIN facility_type t ON t.id = f.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';
DELETE mr FROM maintenance_record mr
    JOIN facility f ON f.id = mr.facility_id
    JOIN facility_type t ON t.id = f.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';
DELETE le FROM facility_lifecycle_event le
    JOIN facility f ON f.id = le.facility_id
    JOIN facility_type t ON t.id = f.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';
DELETE ro FROM rectification_order ro
    JOIN facility f ON f.id = ro.facility_id
    JOIN facility_type t ON t.id = f.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';
DELETE rc FROM inspection_record_correction rc
    JOIN inspection_record r ON r.id = rc.record_id
    JOIN facility f ON f.id = r.facility_id
    JOIN facility_type t ON t.id = f.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';
DELETE ph FROM inspection_photo ph
    JOIN inspection_session s ON s.id = ph.session_id
    JOIN facility f ON f.id = s.facility_id
    JOIN facility_type t ON t.id = f.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';
DELETE dr FROM inspection_draft dr
    JOIN inspection_session s ON s.id = dr.session_id
    JOIN facility f ON f.id = s.facility_id
    JOIN facility_type t ON t.id = f.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';
DELETE ir FROM inspection_record ir
    JOIN facility f ON f.id = ir.facility_id
    JOIN facility_type t ON t.id = f.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';
DELETE se FROM inspection_session se
    JOIN facility f ON f.id = se.facility_id
    JOIN facility_type t ON t.id = f.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';
DELETE tk FROM inspection_task tk
    JOIN facility f ON f.id = tk.facility_id
    JOIN facility_type t ON t.id = f.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';
DELETE f FROM facility f
    JOIN facility_type t ON t.id = f.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';

-- 2) 其他类型的巡检计划、检查项、更新与保养规则
DELETE p FROM inspection_plan p
    JOIN facility_type t ON t.id = p.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';
DELETE i FROM inspection_item i
    JOIN facility_type t ON t.id = i.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';
DELETE r2 FROM facility_update_rule r2
    JOIN facility_type t ON t.id = r2.facility_type_id
WHERE t.type_code <> 'FIRE_HYDRANT';

-- 3) 类型本身
DELETE FROM facility_type WHERE type_code <> 'FIRE_HYDRANT';
