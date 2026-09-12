-- 检查项编号统一改为纯数字：按设施类型内巡检顺序（sort_order,id）从 001 开始重新编号，
-- 与管理端新增时系统自动生成三位数字编号的规则保持一致；
-- 部件档案（facility_component）引用的旧编号一并同步，避免悬空引用。

-- 1) 同步部件档案中引用的旧编号（当前无部件数据时为空操作）
UPDATE facility_component fc
JOIN facility f ON f.id = fc.facility_id
JOIN (
    SELECT i.id,
           i.facility_type_id,
           i.item_code AS old_code,
           LPAD(ROW_NUMBER() OVER (PARTITION BY i.facility_type_id ORDER BY i.sort_order, i.id), 3, '0') AS new_code
    FROM inspection_item i
) m ON m.facility_type_id = f.facility_type_id AND m.old_code = fc.item_code
SET fc.item_code = m.new_code;

-- 2) 重编检查项编号（旧编号均为非数字，重写过程不会触发唯一键冲突）
UPDATE inspection_item item
JOIN (
    SELECT i.id,
           LPAD(ROW_NUMBER() OVER (PARTITION BY i.facility_type_id ORDER BY i.sort_order, i.id), 3, '0') AS new_code
    FROM inspection_item i
) m ON m.id = item.id
SET item.item_code = m.new_code;
